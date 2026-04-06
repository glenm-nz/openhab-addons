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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants;
import org.openhab.binding.elitealarm.internal.handler.EliteAlarmBridgeHandler;
import org.openhab.binding.elitealarm.internal.handler.EliteAlarmThingHandler;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.types.State;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;

@ExtendWith(MockitoExtension.class)
@NonNullByDefault
public class EliteAlarmProtocolTest {

    private @Nullable EliteAlarmMessageListener mockListener;
    private @Nullable EmbeddedChannel channel;

    @BeforeEach
    @SuppressWarnings("null")
    void setup() {
        EliteAlarmMessageListener listener = mock(EliteAlarmMessageListener.class);
        this.mockListener = listener;

        EliteAlarmHandler handler = new EliteAlarmHandler(listener, "admin", "1234");
        EmbeddedChannel ch = new EmbeddedChannel(new StringDecoder(), new StringEncoder(), handler);
        this.channel = ch;

        // Transition to READY for general parsing tests
        ch.writeInbound("Welcome\n");

        // Drain the initial STATUS message triggered by Welcome
        readString(ch);
    }

    @ParameterizedTest(name = "Scenario: {0}")
    @MethodSource("provideProtocolLines")
    @SuppressWarnings("null")
    void testProtocolParsing(String name, String rawLine, String expectedSig, Object expectedValue) {
        AtomicReference<@Nullable ProtocolMatch> capturedMatch = new AtomicReference<>();
        @Nullable
        EliteAlarmMessageListener listener = this.mockListener;
        @Nullable
        EmbeddedChannel ch = this.channel;

        if (listener == null || ch == null) {
            return;
        }

        doAnswer(invocation -> {
            ProtocolMatch arg = invocation.getArgument(0);
            capturedMatch.set(arg);
            return null;
        }).when(listener).onMessageReceived(any(ProtocolMatch.class));

        ch.writeInbound(rawLine + "\n");

        ProtocolMatch match = capturedMatch.get();
        assertNotNull(match, "No ProtocolMatch was captured for: " + name);
        assertEquals(expectedSig, match.signature, "Signature mismatch");

        if ("OKVERSION".equals(expectedSig) || "DU".equals(expectedSig)) {
            assertEquals(expectedValue, match.data1, "Data1 mismatch");
        } else {
            assertEquals(expectedValue, match.output, "Output value mismatch");
        }
    }

    @Test
    @SuppressWarnings("null")
    void testAuthenticationFlow() {
        EliteAlarmMessageListener lis = mock(EliteAlarmMessageListener.class);

        EliteAlarmHandler authHandler = new EliteAlarmHandler(lis, "admin", "1234");
        EmbeddedChannel authChannel = new EmbeddedChannel(new StringDecoder(), new StringEncoder(), authHandler);

        // 1. Handshake: Username
        authChannel.writeInbound("Username:\n");
        String userResp = readString(authChannel).trim();
        assertEquals("admin", userResp);

        // 2. Handshake: Password
        authChannel.writeInbound("Password:\n");
        String passResp = readString(authChannel).trim();
        assertEquals("1234", passResp);

        // 3. Handshake: Welcome
        authChannel.writeInbound("Welcome\n");

        // The handler is not responsible for sending STATUS, but for notifying the
        // listener. We verify the listener is notified, which is the correct behavior.
        verify(lis).onConnectionStateChanged(true);

        // 4. Verify Protocol Processing
        authChannel.writeInbound("ZO5\n");
        verify(lis, atLeastOnce()).onMessageReceived(any(ProtocolMatch.class));
    }

    @SuppressWarnings("null")
    private String readString(EmbeddedChannel ch) {
        Object obj = ch.readOutbound();
        if (obj instanceof io.netty.buffer.ByteBuf) {
            io.netty.buffer.ByteBuf buf = (io.netty.buffer.ByteBuf) obj;
            try {
                String result = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
                return result != null ? result : "";
            } finally {
                buf.release();
            }
        }
        if (obj != null) {
            return obj.toString();
        }
        return "";
    }

    @Test
    @SuppressWarnings("null")
    void testConnectionTimeout() {
        @Nullable
        EmbeddedChannel ch = this.channel;
        @Nullable
        EliteAlarmMessageListener lis = this.mockListener;

        if (ch != null && lis != null) {
            ch.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

            verify(lis, atLeastOnce()).onConnectionStateChanged(false);
            verify(lis, atLeastOnce()).onConnectionLost();
        }
    }

    @Test
    @SuppressWarnings("null")
    void testManualDisconnect() {
        @Nullable
        EmbeddedChannel ch = this.channel;
        @Nullable
        EliteAlarmMessageListener lis = this.mockListener;
        if (ch == null || lis == null)
            return;

        ch.close();

        verify(lis, atLeastOnce()).onConnectionStateChanged(false);
    }

    @ParameterizedTest(name = "Scenario: {0} with data1")
    @MethodSource("provideProtocolLinesWithData1")
    @SuppressWarnings("null")
    void testProtocolParsingWithData1(String name, String rawLine, String expectedSig, String expectedData1,
            Object expectedValue) {
        AtomicReference<@Nullable ProtocolMatch> capturedMatch = new AtomicReference<>();
        @Nullable
        EliteAlarmMessageListener listener = this.mockListener;
        @Nullable
        EmbeddedChannel ch = this.channel;

        if (listener == null || ch == null) {
            return;
        }

        doAnswer(invocation -> {
            ProtocolMatch arg = invocation.getArgument(0);
            capturedMatch.set(arg);
            return null;
        }).when(listener).onMessageReceived(any(ProtocolMatch.class));

        ch.writeInbound(rawLine + "\n");

        ProtocolMatch match = capturedMatch.get();
        assertNotNull(match, "No ProtocolMatch was captured for: " + name);
        assertEquals(expectedSig, match.signature, "Signature mismatch");
        assertEquals(expectedData1, match.data1, "Data1 mismatch");
        assertEquals(expectedValue, match.output, "Output value mismatch");
    }

    @Test
    @SuppressWarnings("null")
    void testBridgeSyncLogic() throws Exception {
        // 1. Setup Mocks
        Bridge bridge = mock(Bridge.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        EliteAlarmNettyClient client = mock(EliteAlarmNettyClient.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        // Mock Things and Channels
        Thing zoneThing1 = mock(Thing.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        Thing zoneThing2 = mock(Thing.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        EliteAlarmThingHandler handler1 = mock(EliteAlarmThingHandler.class);
        EliteAlarmThingHandler handler2 = mock(EliteAlarmThingHandler.class);

        ThingUID thingUID1 = new ThingUID(EliteAlarmBindingConstants.BINDING_ID, "zone", "bridge", "zone1");
        ThingUID thingUID2 = new ThingUID(EliteAlarmBindingConstants.BINDING_ID, "zone", "bridge", "zone2");

        when(zoneThing1.getUID()).thenReturn(thingUID1);
        when(zoneThing2.getUID()).thenReturn(thingUID2);
        when(zoneThing1.getHandler()).thenReturn(handler1);
        when(zoneThing2.getHandler()).thenReturn(handler2);
        when(zoneThing1.getThingTypeUID()).thenReturn(EliteAlarmBindingConstants.THING_TYPE_ZONE);
        when(zoneThing2.getThingTypeUID()).thenReturn(EliteAlarmBindingConstants.THING_TYPE_ZONE);
        when(zoneThing1.getConfiguration()).thenReturn(new Configuration(Collections.singletonMap("zoneNumber", 1)));
        when(zoneThing2.getConfiguration()).thenReturn(new Configuration(Collections.singletonMap("zoneNumber", 2)));

        Channel channel = mock(Channel.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(channel.getUID().getId()).thenReturn(EliteAlarmBindingConstants.CHANNEL_ZONE_UNSEALED);
        when(channel.getAcceptedItemType()).thenReturn("Switch");

        when(zoneThing1.getChannels()).thenReturn(Collections.singletonList(channel));
        when(zoneThing2.getChannels()).thenReturn(Collections.singletonList(channel)); // Same channel type

        when(bridge.getThings()).thenReturn(List.of(zoneThing1, zoneThing2));

        // 2. Initialize Handler and inject mocks
        EliteAlarmBridgeHandler bridgeHandler = new EliteAlarmBridgeHandler(bridge);
        Field schedulerField = org.openhab.core.thing.binding.BaseThingHandler.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(bridgeHandler, scheduler);

        Field connectorField = EliteAlarmBridgeHandler.class.getDeclaredField("connector");
        connectorField.setAccessible(true);
        connectorField.set(bridgeHandler, client);

        // 3. Start the sync process
        ArgumentCaptor<Runnable> syncEndCaptor = ArgumentCaptor.forClass(Runnable.class);
        bridgeHandler.onConnectionStateChanged(true);

        // Verify sync started and commands sent
        verify(client).sendCommand("STATUS\n");
        verify(scheduler).schedule(syncEndCaptor.capture(), any(Long.class), any(TimeUnit.class));

        // 4. Simulate receiving a message for Zone 2 only
        ProtocolMatch match = new ProtocolMatch();
        match.subsystem = "Zone";
        match.zone = 2;
        match.channelId = EliteAlarmBindingConstants.CHANNEL_ZONE_UNSEALED;
        match.output = 1; // ON
        bridgeHandler.onMessageReceived(match);

        // Verify Zone 2 was updated during sync
        ChannelUID channelUID2 = new ChannelUID(zoneThing2.getUID(), EliteAlarmBindingConstants.CHANNEL_ZONE_UNSEALED);
        verify(handler2).updateState(eq(channelUID2), eq(OnOffType.ON));

        // 5. Manually trigger the end of the sync window
        Runnable endSyncProcess = syncEndCaptor.getValue();
        endSyncProcess.run();

        // 6. Verify stale channel (Zone 1) was reset
        ChannelUID channelUID1 = new ChannelUID(zoneThing1.getUID(), EliteAlarmBindingConstants.CHANNEL_ZONE_UNSEALED);
        verify(handler1, times(1)).updateState(eq(channelUID1), eq(OnOffType.OFF));

        // Verify Zone 2 was NOT reset again at the end, and was only updated once
        verify(handler2, times(1)).updateState(any(ChannelUID.class), any(State.class));
    }

    @SuppressWarnings({ "null" })
    private static Stream<Arguments> provideProtocolLinesWithData1() {
        return Stream.of(Arguments.of("Pendant Battery Low", "PBF1", "PBF", "Pendant 1", 1),
                Arguments.of("Pendant Battery Restore", "PBR1", "PBR", "Pendant 1", 0),
                Arguments.of("Armed Away by User", "A1-U1", "AU", "User 1", 1),
                Arguments.of("Armed Stay by User", "S1-U1", "SU", "User 1", 1),
                Arguments.of("Area Disarmed User", "D1-U5", "DU", "User 5", 0),
                Arguments.of("Zone Entry Delay", "ZEDS1-10", "ZEDS", "10s", null));
    }

    @SuppressWarnings({ "null" })
    private static Stream<Arguments> provideProtocolLines() {
        return Stream.of(Arguments.of("Version Info", "OK Version \"ECi FW Ver. 10.3.54\"", "OKVERSION", "ECi"),
                Arguments.of("Device Query", "OK Device 32", "OKDEVICE", 32),
                Arguments.of("Panel Mode", "OK MODE 4", "OKMODE", 4), Arguments.of("Zone Unsealed", "ZO12", "ZO", 1),
                Arguments.of("Zone Sealed", "ZC12", "ZC", 0), Arguments.of("Zone Trouble", "ZT1", "ZT", 1),
                Arguments.of("Zone Trouble Restore", "ZTR1", "ZTR", 0), Arguments.of("Zone Alarm", "ZA1", "ZA", 1),
                Arguments.of("Zone Alarm Restore", "ZR1", "ZR", 0), Arguments.of("Zone Bypass", "ZBY1", "ZBY", 1),
                Arguments.of("Zone Bypass Restore", "ZBYR1", "ZBYR", 0),
                Arguments.of("Zone Battery Low", "ZBL1", "ZBL", 1),
                Arguments.of("Zone Battery Restore", "ZBR1", "ZBR", 0),
                Arguments.of("Zone Supervise Alarm", "ZSA1", "ZSA", 1),
                Arguments.of("Zone Supervise Restore", "ZSR1", "ZSR", 0),
                Arguments.of("Zone Sensor Watch Alarm", "ZIA1", "ZIA", 1),
                Arguments.of("Zone Sensor Watch Restore", "ZIR1", "ZIR", 0), Arguments.of("Output On", "OO1", "OO", 1),
                Arguments.of("Output Off", "OR1", "OR", 0), Arguments.of("Area Armed", "A1", "A", 1),
                Arguments.of("Mains Fail", "MF", "MF", 1), Arguments.of("Mains Restore", "MR", "MR", 0),
                Arguments.of("Dialer Failure", "DF", "DF", 1), Arguments.of("Dialer Restore", "DR", "DR", 0),
                Arguments.of("Line Failure", "LF", "LF", 1), Arguments.of("Line Restore", "LR", "LR", 0),
                Arguments.of("Communication Active", "CAL", "CAL", 1),
                Arguments.of("Communication Finished", "CLF", "CLF", 0),
                Arguments.of("Receiver Failure", "RIF", "RIF", 1), Arguments.of("Receiver Restore", "RIR", "RIR", 0),
                Arguments.of("Panic Alarm", "PA", "PA", 1), Arguments.of("Panic Clear", "PC", "PC", 0),
                Arguments.of("Fire Alarm", "FA", "FA", 1), Arguments.of("Fire Clear", "FC", "FC", 0),
                Arguments.of("Medical Alarm", "MA", "MA", 1), Arguments.of("Medical Clear", "MC", "MC", 0),
                Arguments.of("Exit Delay Away", "EA1", "EA", 1), Arguments.of("Exit Delay Stay", "ES1", "ES", 1),
                Arguments.of("Exit Delay Away with Time", "EDA1-10", "EDA", 1),
                Arguments.of("Exit Delay Stay with Time", "EDS1-10", "EDS", 1));
    }
}
