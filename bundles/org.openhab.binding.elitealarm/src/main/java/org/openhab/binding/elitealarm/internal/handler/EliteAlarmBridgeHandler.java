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
package org.openhab.binding.elitealarm.internal.handler;

import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_ARMED_AWAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_EXIT_DELAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_LAST_USER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_READY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_RAW_COMMAND;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_AREA_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_EXPANDER_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_HOST;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_OUTPUT_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_PORT;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_RECONNECT;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_REFRESH;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_ZONE_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_AREA;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_INPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_PROX_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_SYSTEM;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_ZONE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.elitealarm.internal.protocol.EliteAlarmMessageListener;
import org.openhab.binding.elitealarm.internal.protocol.EliteAlarmNettyClient;
import org.openhab.binding.elitealarm.internal.protocol.ProtocolMatch;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class EliteAlarmBridgeHandler extends BaseBridgeHandler implements EliteAlarmMessageListener {
    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(EliteAlarmBridgeHandler.class);
    private @Nullable EliteAlarmNettyClient connector;
    private boolean isSyncing = false;
    private final Set<ChannelUID> updatedChannelsInSync = new HashSet<>();
    private @Nullable ScheduledFuture<?> syncEndFuture;

    public EliteAlarmBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        Configuration config = getConfig();
        String host = (String) config.get(CONFIG_HOST);
        int port = getIntConfig(CONFIG_PORT, 0);

        if (host == null || port <= 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Missing IP/Port");
            return;
        }

        String user = config.get("username") instanceof String s ? s : "";
        String pass = config.get("password") instanceof String s ? s : "";
        int refresh = getIntConfig(CONFIG_REFRESH, 60);
        int reconnect = getIntConfig(CONFIG_RECONNECT, 10);

        updateStatus(ThingStatus.UNKNOWN);

        EliteAlarmNettyClient client = new EliteAlarmNettyClient(host, port, user, pass, refresh, reconnect, this);
        this.connector = client;
        client.start();
    }

    private int getIntConfig(String key, int defaultValue) {
        Object val = getConfig().get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
            }
        }
        return defaultValue;
    }

    @Override
    public void onConnectionLost() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Panel connection lost");
        setChildStatuses(ThingStatus.OFFLINE);
    }

    @Override
    public void onConnectionStateChanged(boolean online) {
        if (online) {
            updateStatus(ThingStatus.ONLINE);
            // Cancel any previously running sync-end task.
            if (syncEndFuture != null) {
                syncEndFuture.cancel(false);
            }

            logger.debug("Connection online. Starting 5-second sync window.");
            isSyncing = true;
            updatedChannelsInSync.clear();

            EliteAlarmNettyClient client = connector;
            if (client != null) {
                client.sendCommand("VERSION\n");
                client.sendCommand("MODE ?\n");
                client.sendCommand("DEVICE ?\n");
                client.sendCommand("STATUS\n");
            }

            // Schedule a task to end the sync process after a delay.
            syncEndFuture = scheduler.schedule(this::endSyncProcess, 5, TimeUnit.SECONDS);

        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "No connection to panel");
            if (syncEndFuture != null) {
                syncEndFuture.cancel(false);
            }
            isSyncing = false;
        }
        setChildStatuses(online ? ThingStatus.ONLINE : ThingStatus.OFFLINE);
    }

    private void setChildStatuses(ThingStatus status) {
        for (Thing child : getThing().getThings()) {
            var handler = child.getHandler();
            if (handler instanceof EliteAlarmThingHandler) {
                ((EliteAlarmThingHandler) handler).updateStatus(status);
            }
        }
    }

    @Override
    public void onMessageReceived(ProtocolMatch match) {
        logger.debug("Received Subsystem: {}, Signature: {}", match.subsystem, match.signature);

        switch (match.subsystem) {
            case "Zone":
                handleZoneUpdate(match);
                break;
            case "Area":
                handleAreaUpdate(match);
                break;
            case "System":
                handleSystemUpdate(match);
                break;
            case "Output":
                handleOutputUpdate(match);
                break;
            case "InputExpander":
                handleExpanderUpdate(match, THING_TYPE_INPUT_EXPANDER);
                break;
            case "OutputExpander":
                handleExpanderUpdate(match, THING_TYPE_OUTPUT_EXPANDER);
                break;
            case "ProxExpander":
                handleExpanderUpdate(match, THING_TYPE_PROX_EXPANDER);
                break;
            default:
                break;
        }
        // Update raw command channel on the system thing
        for (Thing child : getThing().getThings()) {
            if (child.getThingTypeUID().equals(THING_TYPE_SYSTEM)) {
                updateChildChannel(child, CHANNEL_SYSTEM_RAW_COMMAND, new StringType(match.rawMessage));
            }
        }
    }

    private void handleSystemUpdate(ProtocolMatch match) {
        if ("OKVERSION".equals(match.signature)) {
            updateSystemProperty(Thing.PROPERTY_MODEL_ID, match.data1);
            updateSystemProperty(Thing.PROPERTY_FIRMWARE_VERSION, match.data2);
        } else if ("OKMODE".equals(match.signature)) {
            @SuppressWarnings("null")
            int mode = match.output;
            updateSystemProperty("Panel Mode", String.valueOf(mode));
            if (mode != 4) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Panel must be in Mode 4");
            } else {
                if (getThing().getStatus() == ThingStatus.OFFLINE
                        && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR) {
                    updateStatus(ThingStatus.ONLINE);
                }
            }
        } else if ("OKDEVICE".equals(match.signature)) {
            updateSystemProperty("Panel Device ID", String.valueOf(match.output));
        } else if ("OKSTATUS".equals(match.signature)) {
            // This is now just an ack and doesn't trigger any logic.
        } else {
            String cid = match.channelId;
            Integer out = match.output;
            if (cid != null && out != null) {
                for (Thing child : getThing().getThings()) {
                    if (child.getThingTypeUID().equals(THING_TYPE_SYSTEM)) {
                        updateChildChannel(child, cid, out == 1 ? OnOffType.ON : OnOffType.OFF);
                    }
                }
            }
        }
    }

    private void handleZoneUpdate(ProtocolMatch match) {
        Integer zNum = match.zone;
        String cid = match.channelId;
        Integer out = match.output;
        if (zNum == null || cid == null) {
            return;
        }

        updateChildThing(THING_TYPE_ZONE.getId(), CONFIG_ZONE_NUMBER, zNum, (thing) -> {
            if (out != null) {
                updateChildChannel(thing, cid, out == 1 ? OnOffType.ON : OnOffType.OFF);
            } else if (match.data1 != null) {
                updateChildChannel(thing, cid, new StringType(match.data1));
            }
        });
    }

    private void handleAreaUpdate(ProtocolMatch match) {
        Integer aNum = match.area;
        String cid = match.channelId;
        Integer out = match.output;
        if (aNum == null || cid == null || out == null) {
            return;
        }

        updateChildThing(THING_TYPE_AREA.getId(), CONFIG_AREA_NUMBER, aNum, (thing) -> {
            updateChildChannel(thing, cid, out == 1 ? OnOffType.ON : OnOffType.OFF);

            // Turn off exit delay when arming or disarming
            if ("A".equals(match.signature) || "D".equals(match.signature) || "S".equals(match.signature)
                    || "AU".equals(match.signature) || "SU".equals(match.signature) || "DU".equals(match.signature)) {
                updateChildChannel(thing, CHANNEL_AREA_EXIT_DELAY, OnOffType.OFF);
            }

            if ("DISARMED".equals(match.description)) {
                updateChildChannel(thing, CHANNEL_AREA_ARMED_AWAY, OnOffType.OFF);
            }

            // If the message includes user info, update the last-user channel
            if (match.user != null
                    && ("DU".equals(match.signature) || "AU".equals(match.signature) || "SU".equals(match.signature))) {
                updateChildChannel(thing, CHANNEL_AREA_LAST_USER, new StringType("User " + match.user));
            }
        });
    }

    private void handleOutputUpdate(ProtocolMatch match) {
        Integer outIdx = match.outputNumber;
        String cid = match.channelId;
        Integer out = match.output;

        if (outIdx == null || cid == null || out == null) {
            return;
        }

        updateChildThing(THING_TYPE_OUTPUT.getId(), CONFIG_OUTPUT_NUMBER, outIdx, (thing) -> {
            updateChildChannel(thing, cid, out == 1 ? OnOffType.ON : OnOffType.OFF);
        });
    }

    private void handleExpanderUpdate(ProtocolMatch match, ThingTypeUID thingType) {
        Integer expNum = match.expander;
        String cid = match.channelId;
        Integer out = match.output;
        if (expNum == null || cid == null || out == null)
            return;

        updateChildThing(thingType.getId(), CONFIG_EXPANDER_NUMBER, expNum,
                (thing) -> updateChildChannel(thing, cid, out == 1 ? OnOffType.ON : OnOffType.OFF));
    }

    private void updateSystemProperty(String key, @Nullable String value) {
        if (value == null)
            return;
        Map<String, @Nullable String> properties = new HashMap<>(getThing().getProperties());
        properties.put(key, value);
        updateProperties(properties);
    }

    private void updateChildChannel(Thing child, String channelId, State state) {
        ChannelUID channelUID = new ChannelUID(child.getUID(), channelId);
        if (isSyncing) {
            updatedChannelsInSync.add(channelUID);
        }
        var handler = child.getHandler();
        if (handler instanceof EliteAlarmThingHandler) {
            ((EliteAlarmThingHandler) handler).updateState(channelUID, state);
        } else {
            logger.debug("Cannot update channel {} on thing {}: Handler not ready.", channelId, child.getUID());
        }
    }

    private void updateChildChannel(Thing child, String channelId, OnOffType state) {
        updateChildChannel(child, channelId, (State) state);
    }

    private void updateChildThing(String thingTypeId, String configKey, int matchValue, Consumer<Thing> action) {
        for (Thing child : getThing().getThings()) {
            if (child.getThingTypeUID().getId().equals(thingTypeId)) {
                int cfgVal = getIntConfig(child, configKey);
                if (cfgVal == matchValue) {
                    logger.debug("Updating child {} for {}={}", child.getUID(), configKey, matchValue);
                    action.accept(child);
                    return;
                }
            }
        }
        logger.debug("No child found with Type: {}, {}={}", thingTypeId, configKey, matchValue);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        EliteAlarmNettyClient client = connector;
        if (client == null || getThing().getStatus() != ThingStatus.ONLINE)
            return;

        if (!(command instanceof OnOffType)) {
            logger.debug("Command type '{}' not supported for channel '{}'", command.getClass().getSimpleName(),
                    channelUID);
            return;
        }

        for (Thing child : getThing().getThings()) {
            if (child.getUID().equals(channelUID.getThingUID())) {
                String typeId = child.getThingTypeUID().getId();
                if (typeId.equals(THING_TYPE_OUTPUT.getId())) {
                    int num = getIntConfig(child, CONFIG_OUTPUT_NUMBER);
                    client.sendCommand((command == OnOffType.ON ? "ON" : "OFF") + num + "\n");
                } else if (typeId.equals(THING_TYPE_AREA.getId())
                        && channelUID.getId().equals(CHANNEL_AREA_ARMED_AWAY)) {
                    int num = getIntConfig(child, CONFIG_AREA_NUMBER);
                    client.sendCommand((command == OnOffType.ON ? "ARM" : "DISARM") + num + "\n");
                }
                return;
            }
        }
    }

    private int getIntConfig(Thing thing, String key) {
        Object val = thing.getConfiguration().get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                /* ignore */ }
        }
        return 0;
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Configuration configuration = editConfiguration();
        for (Map.Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
        }
        updateConfiguration(configuration);

        // Disconnect and reconnect gracefully without tearing down the whole handler
        EliteAlarmNettyClient client = connector;
        connector = null;
        if (client != null) {
            client.stop();
        }
        if (syncEndFuture != null) {
            syncEndFuture.cancel(false);
            syncEndFuture = null;
        }
        isSyncing = false;

        initialize();
    }

    @Override
    public void dispose() {
        if (syncEndFuture != null) {
            syncEndFuture.cancel(false);
        }
        EliteAlarmNettyClient client = connector;
        connector = null;
        if (client != null) {
            client.stop();
        }
        super.dispose();
    }

    private void endSyncProcess() {
        if (!isSyncing) {
            return; // Process was already ended or cancelled.
        }
        logger.debug("Sync window finished. Resetting stale channels.");
        isSyncing = false;
        resetStaleChannels();
    }

    private void resetStaleChannels() {
        for (Thing child : getThing().getThings()) {
            for (Channel channel : child.getChannels()) {
                ChannelUID channelUID = new ChannelUID(child.getUID(), channel.getUID().getId());
                if (!updatedChannelsInSync.contains(channelUID)) {
                    State normalState = getNormalStateForChannel(channel);
                    if (normalState != null) {
                        updateChildChannel(child, channel.getUID().getId(), normalState);
                    }
                }
            }
        }
    }

    private @Nullable State getNormalStateForChannel(Channel channel) {
        String channelId = channel.getUID().getId();
        String itemType = channel.getAcceptedItemType();

        if ("Switch".equals(itemType)) {
            if (channelId.equals(CHANNEL_AREA_READY)) {
                return OnOffType.ON;
            }
            return OnOffType.OFF;
        } else if ("String".equals(itemType)) {
            return StringType.EMPTY;
        }
        return null;
    }
}
