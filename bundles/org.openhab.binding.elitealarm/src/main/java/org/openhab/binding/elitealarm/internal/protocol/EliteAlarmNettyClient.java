/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.elitealarm.internal.protocol;

import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;

@NonNullByDefault
public class EliteAlarmNettyClient {
    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(EliteAlarmNettyClient.class);

    private final String host;
    private final int port;
    private final String user;
    private final String pass;
    private final int refreshInterval;
    private final int reconnectInterval;
    private final EliteAlarmMessageListener listener;

    private @Nullable EventLoopGroup group;
    private @Nullable Channel channel;

    private volatile boolean shuttingDown = false;

    public EliteAlarmNettyClient(String host, int port, String user, String pass, int refreshInterval,
            int reconnectInterval, EliteAlarmMessageListener listener) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.pass = pass;
        this.refreshInterval = refreshInterval;
        this.reconnectInterval = reconnectInterval;
        this.listener = listener;
    }

    public void start() {
        shuttingDown = false;
        group = new NioEventLoopGroup();
        connect();
    }

    public void connect() {
        EventLoopGroup currentGroup = this.group;

        if (currentGroup == null || shuttingDown) {
            return;
        }

        Bootstrap b = new Bootstrap();
        b.group(currentGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                int readerTimeout = (refreshInterval * 2) + 5;
                ch.pipeline().addLast(new IdleStateHandler(readerTimeout, refreshInterval, 0));
                ch.pipeline().addLast(new StringDecoder());
                ch.pipeline().addLast(new StringEncoder());
                ch.pipeline().addLast(new EliteAlarmHandler(listener, user, pass));
            }
        });

        logger.debug("Connecting to Elite Alarm panel at {}:{}", host, port);

        b.connect(host, port).addListener(future -> {
            if (future.isSuccess()) {
                // 1. Capture the channel from the future into a local variable
                io.netty.channel.Channel newChannel = ((io.netty.channel.ChannelFuture) future).channel();
                this.channel = newChannel;

                logger.debug("Socket connected successfully.");

                // 2. Use the local variable
                newChannel.closeFuture().addListener(closeFuture -> {
                    if (!shuttingDown) {
                        logger.warn("Connection to Elite Alarm lost. Retrying in {} seconds...", reconnectInterval);
                        listener.onConnectionStateChanged(false);

                        // Use the event loop from the closed channel to schedule the next attempt
                        newChannel.eventLoop().schedule(this::connect, reconnectInterval, TimeUnit.SECONDS);
                    }
                });
            } else if (!shuttingDown) {
                // Retry logic for initial connection failure
                currentGroup.schedule(this::connect, reconnectInterval, TimeUnit.SECONDS);
            }
        });
    }

    public void sendCommand(String command) {
        Channel currentChannel = channel;

        if (currentChannel != null && currentChannel.isActive()) {
            String cleanCommand = command.trim() + "\n";
            logger.debug("Sending command to panel: {}", cleanCommand.replace("\n", "\\n"));
            currentChannel.writeAndFlush(cleanCommand);
        } else if (!shuttingDown) {
            logger.warn("Cannot send command '{}': Panel is not connected.", command.trim());
        }
    }

    public void stop() {
        logger.debug("Stopping Elite Alarm Netty client...");
        shuttingDown = true;

        // Capture fields to local variables
        Channel ch = this.channel;
        EventLoopGroup grp = this.group;

        // Clear the fields
        this.channel = null;
        this.group = null;

        // Use the local variables
        if (ch != null) {
            ch.writeAndFlush("EXIT\n");
            ch.close();
        }

        if (grp != null) {
            grp.shutdownGracefully();
        }
    }
}
