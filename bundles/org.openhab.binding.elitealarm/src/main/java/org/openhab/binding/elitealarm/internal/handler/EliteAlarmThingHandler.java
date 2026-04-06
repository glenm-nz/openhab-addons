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

import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_EXPANDER_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_OUTPUT_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CONFIG_ZONE_NUMBER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_INPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_PROX_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_ZONE;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class EliteAlarmThingHandler extends BaseThingHandler {
    @SuppressWarnings("null")
    private final Logger logger = LoggerFactory.getLogger(EliteAlarmThingHandler.class);

    public EliteAlarmThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        if (!validateConfiguration()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Invalid configuration parameter range");
            return;
        }

        EliteAlarmBridgeHandler bridgeHandler = getEliteAlarmBridgeHandler();
        if (bridgeHandler != null) {
            // Match the bridge's current status immediately
            updateStatus(bridgeHandler.getThing().getStatus());
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    private boolean validateConfiguration() {
        ThingTypeUID type = getThing().getThingTypeUID();

        if (THING_TYPE_ZONE.equals(type)) {
            int num = getIntConfig(CONFIG_ZONE_NUMBER);
            return num >= 1 && num <= 248;
        }
        if (THING_TYPE_OUTPUT.equals(type)) {
            int num = getIntConfig(CONFIG_OUTPUT_NUMBER);
            return num >= 1 && num <= 32;
        }
        if (THING_TYPE_INPUT_EXPANDER.equals(type)) {
            int num = getIntConfig(CONFIG_EXPANDER_NUMBER);
            return num >= 1 && num <= 30;
        }
        if (THING_TYPE_OUTPUT_EXPANDER.equals(type)) {
            int num = getIntConfig(CONFIG_EXPANDER_NUMBER);
            return num >= 1 && num <= 8;
        }
        if (THING_TYPE_PROX_EXPANDER.equals(type)) {
            int num = getIntConfig(CONFIG_EXPANDER_NUMBER);
            return num >= 1 && num <= 32;
        }
        return true;
    }

    public static int getIntConfig(Thing thing, String key) {
        Object val = thing.getConfiguration().get(key);
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
            }
        }
        return 0;
    }

    private int getIntConfig(String key) {
        return getIntConfig(getThing(), key);
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Configuration configuration = editConfiguration();
        for (Map.Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
        }
        updateConfiguration(configuration);

        // Re-evaluate configuration and update status accordingly
        initialize();
    }

    @Override
    public void dispose() {
        // Ensure the thing reflects an offline state when being removed or disabled
        updateStatus(ThingStatus.OFFLINE);
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        EliteAlarmBridgeHandler bridgeHandler = getEliteAlarmBridgeHandler();
        if (bridgeHandler != null) {
            // Forward commands (like Output ON/OFF) to the bridge for processing
            bridgeHandler.handleCommand(channelUID, command);
        }
    }

    // --- Start Visibility Elevation ---
    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    @Override
    public void updateState(ChannelUID channelUID, State state) {
        logger.debug("Updating state for channel {}: {}", channelUID, state);
        super.updateState(channelUID, state);
    }
    // --- End Visibility Elevation ---

    private @Nullable EliteAlarmBridgeHandler getEliteAlarmBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            return null;
        }
        ThingHandler handler = bridge.getHandler();
        if (handler instanceof EliteAlarmBridgeHandler) {
            return (EliteAlarmBridgeHandler) handler;
        }
        return null;
    }
}
