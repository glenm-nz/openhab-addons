/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

@NonNullByDefault
public class EliteAlarmHandler extends SimpleChannelInboundHandler<String> {
	@SuppressWarnings("null")
	private final Logger logger = LoggerFactory.getLogger(EliteAlarmHandler.class);
	private final EliteAlarmMessageListener listener;
	private final String username;
	private final String password;

	private enum SessionState {
		OFFLINE, WAITING_AUTH, READY
	}

	private SessionState state = SessionState.WAITING_AUTH;
	private final StringBuilder buffer = new StringBuilder();

	public EliteAlarmHandler(EliteAlarmMessageListener listener, String username, String password) {
		this.listener = listener;
		this.username = username;
		this.password = password;
	}

	@Override
	public void channelActive(@Nullable ChannelHandlerContext ctx) {
		logger.debug("TCP Connection established. Waiting for prompt or Welcome...");
	}

	@Override
	protected void channelRead0(@Nullable ChannelHandlerContext ctx, @Nullable String msg) {
		if (msg == null || ctx == null) {
			return;
		}
		buffer.append(msg);
		processBuffer(ctx);
	}

	private void processBuffer(ChannelHandlerContext ctx) {
		int newlineIndex;
		while ((newlineIndex = buffer.indexOf("\n")) != -1) {
			String line = buffer.substring(0, newlineIndex).trim();
			buffer.delete(0, newlineIndex + 1);

			if (line.isEmpty()) {
				continue;
			}

			handleProtocolLine(ctx, line);
		}

		// Safety valve for malformed data
		if (buffer.length() > 4096) {
			logger.warn("Buffer threshold exceeded (4KB) without newline. Clearing.");
			buffer.setLength(0);
		}
	}

	private void transitionToReady(@Nullable ChannelHandlerContext ctx) {
		if (state != SessionState.READY) {
			state = SessionState.READY;
			logger.debug("Elite Alarm connection is READY. Sending initial status request.");

			listener.onConnectionStateChanged(true);
		}
	}

	private void handleProtocolLine(ChannelHandlerContext ctx, String line) {
		String trimmed = java.util.Objects.requireNonNull(line.trim());
		if (trimmed.isEmpty())
			return;

		// Extract signature (e.g., "OK Version" -> "OKVERSION")
		String signature = java.util.Objects
				.requireNonNull(trimmed.toUpperCase().split("\"")[0].replaceAll("[^A-Z]", ""));

		EliteAlarmProtocolRegistry.findMatch(signature, trimmed).ifPresentOrElse(def -> {
			java.util.regex.Matcher m = def.pattern().matcher(trimmed);
			if (m.matches()) {
				ProtocolMatch match = new ProtocolMatch();
				match.signature = java.util.Objects.requireNonNull(def.signature());
				match.description = java.util.Objects.requireNonNull(def.description());
				match.subsystem = java.util.Objects.requireNonNull(def.subsystem());
				match.rawMessage = trimmed;
				match.channelId = def.channelId();

				String[] groups = new String[m.groupCount()];
				for (int i = 1; i <= m.groupCount(); i++) {
					groups[i - 1] = java.util.Objects.requireNonNullElse(m.group(i), "");
				}

				def.mapper().accept(match, groups);
				logger.debug("Protocol Match found: {}", match);

				if ("Protocol".equals(match.subsystem)) {
					handleProtocolSubsystem(ctx, match);
				} else if (state == SessionState.READY) {
					listener.onMessageReceived(match);
				}
			}
		}, () -> logger.debug("No registry match found for signature '{}'. Raw line: [{}]", signature, trimmed));
	}

	private void handleProtocolSubsystem(ChannelHandlerContext ctx, ProtocolMatch match) {
		switch (match.signature) {
			case "USERNAME" :
				logger.debug("Received username prompt. Sending credentials...");
				ctx.writeAndFlush(username + "\n");
				break;
			case "PASSWORD" :
				logger.debug("Received password prompt. Sending credentials...");
				ctx.writeAndFlush(password + "\n");
				break;
			case "WELCOME" :
				logger.debug("Authentication successful via 'Welcome' message.");
				transitionToReady(ctx);
				break;
			case "OK" :
				// "OK" can be a sign-on confirmation or a generic ack
				if (state != SessionState.READY) {
					transitionToReady(ctx);
				}
				break;
		}
	}

	@Override
	public void userEventTriggered(@Nullable ChannelHandlerContext ctx, @Nullable Object evt) {
		if (evt instanceof IdleStateEvent && ctx != null) {
			IdleStateEvent e = (IdleStateEvent) evt;
			if (e.state() == IdleState.WRITER_IDLE && state == SessionState.READY) {
				ctx.writeAndFlush("STATUS\n");
			} else if (e.state() == IdleState.READER_IDLE) {
				logger.warn("Reader idle timeout reached. Closing connection.");
				state = SessionState.OFFLINE;
				listener.onConnectionLost();
				listener.onConnectionStateChanged(false);
				ctx.close();
			}
		}
	}

	@Override
	public void channelInactive(@Nullable ChannelHandlerContext ctx) throws Exception {
		if (state != SessionState.OFFLINE) {
			state = SessionState.OFFLINE;
			listener.onConnectionStateChanged(false);
		}
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(@Nullable ChannelHandlerContext ctx, @Nullable Throwable cause) {
		logger.warn("Exception caught in EliteAlarm Netty handler: {}", cause != null ? cause.getMessage() : "Unknown",
				cause);
		if (ctx != null)
			ctx.close();
	}
}
