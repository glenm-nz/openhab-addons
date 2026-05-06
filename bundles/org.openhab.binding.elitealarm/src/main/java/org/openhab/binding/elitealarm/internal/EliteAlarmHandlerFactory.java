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

package org.openhab.binding.elitealarm.internal;

import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_AREA;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_BRIDGE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_INPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_OUTPUT_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_PROX_EXPANDER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_SYSTEM;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.THING_TYPE_ZONE;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.elitealarm.internal.handler.EliteAlarmBridgeHandler;
import org.openhab.binding.elitealarm.internal.handler.EliteAlarmThingHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link EliteAlarmHandlerFactory} is responsible for creating things and
 * thing handlers.
 *
 * @author Glen McGeachen - Initial contribution with input from Gemini
 */

@NonNullByDefault
@Component(configurationPid = "binding.elitealarm", service = ThingHandlerFactory.class)
public class EliteAlarmHandlerFactory extends BaseThingHandlerFactory {

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return EliteAlarmBindingConstants.SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new EliteAlarmBridgeHandler((Bridge) thing);
        } else if (THING_TYPE_ZONE.equals(thingTypeUID) || THING_TYPE_AREA.equals(thingTypeUID)
                || THING_TYPE_OUTPUT.equals(thingTypeUID) || THING_TYPE_SYSTEM.equals(thingTypeUID)
                || THING_TYPE_INPUT_EXPANDER.equals(thingTypeUID) || THING_TYPE_OUTPUT_EXPANDER.equals(thingTypeUID)
                || THING_TYPE_PROX_EXPANDER.equals(thingTypeUID)) {
            return new EliteAlarmThingHandler(thing);
        }

        return null;
    }
}
