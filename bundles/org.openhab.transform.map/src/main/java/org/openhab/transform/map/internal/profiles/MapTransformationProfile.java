/**
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
package org.openhab.transform.map.internal.profiles;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.profiles.ProfileCallback;
import org.openhab.core.thing.profiles.ProfileContext;
import org.openhab.core.thing.profiles.ProfileTypeUID;
import org.openhab.core.thing.profiles.TimeSeriesProfile;
import org.openhab.core.transform.TransformationException;
import org.openhab.core.transform.TransformationHelper;
import org.openhab.core.transform.TransformationService;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.TimeSeries;
import org.openhab.core.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profile to offer the MapTransformationservice on an ItemChannelLink
 *
 * @author Stefan Triller - Initial contribution
 */
@NonNullByDefault
public class MapTransformationProfile implements TimeSeriesProfile {

    public static final ProfileTypeUID PROFILE_TYPE_UID = new ProfileTypeUID(
            TransformationService.TRANSFORM_PROFILE_SCOPE, "MAP");

    private final Logger logger = LoggerFactory.getLogger(MapTransformationProfile.class);

    private final TransformationService service;
    private final ProfileCallback callback;

    private static final String FUNCTION_PARAM = "function";
    private static final String SOURCE_FORMAT_PARAM = "sourceFormat";

    @NonNullByDefault({})
    private final String function;
    @NonNullByDefault({})
    private final String sourceFormat;

    public MapTransformationProfile(ProfileCallback callback, ProfileContext context, TransformationService service) {
        this.service = service;
        this.callback = callback;

        Object paramFunction = context.getConfiguration().get(FUNCTION_PARAM);
        Object paramSource = context.getConfiguration().get(SOURCE_FORMAT_PARAM);

        logger.debug("Profile configured with '{}'='{}', '{}'={}", FUNCTION_PARAM, paramFunction, SOURCE_FORMAT_PARAM,
                paramSource);
        // SOURCE_FORMAT_PARAM is an advanced parameter and we assume "%s" if it is not set
        if (paramSource == null) {
            paramSource = "%s";
        }
        if (paramFunction instanceof String pFunction && paramSource instanceof String pFormat) {
            function = pFunction;
            sourceFormat = pFormat;
        } else {
            logger.error("Parameter '{}' and '{}' have to be Strings. Profile will be inactive.", FUNCTION_PARAM,
                    SOURCE_FORMAT_PARAM);
            function = null;
            sourceFormat = null;
        }
    }

    @Override
    public ProfileTypeUID getProfileTypeUID() {
        return PROFILE_TYPE_UID;
    }

    @Override
    public void onStateUpdateFromItem(State state) {
    }

    @Override
    public void onCommandFromItem(Command command) {
        callback.handleCommand(command);
    }

    @Override
    public void onCommandFromHandler(Command command) {
        if (function == null || sourceFormat == null) {
            logger.warn(
                    "Please specify a function and a source format for this Profile in the '{}', and '{}' parameters, e.g \"translation.map\"  and \"%s\". Returning the original command now.",
                    FUNCTION_PARAM, SOURCE_FORMAT_PARAM);
            callback.sendCommand(command);
            return;
        }
        callback.sendCommand((Command) transformState(command));
    }

    @Override
    public void onStateUpdateFromHandler(State state) {
        if (function == null || sourceFormat == null) {
            logger.warn(
                    "Please specify a function and a source format for this Profile in the '{}' and '{}' parameters, e.g \"translation.map\" and \"%s\". Returning the original state now.",
                    FUNCTION_PARAM, SOURCE_FORMAT_PARAM);
            callback.sendUpdate(state);
            return;
        }
        callback.sendUpdate((State) transformState(state));
    }

    @Override
    public void onTimeSeriesFromHandler(TimeSeries timeSeries) {
        if (function == null || sourceFormat == null) {
            logger.warn(
                    "Please specify a function and a source format for this Profile in the '{}' and '{}' parameters, e.g \"translation.map\" and \"%s\". Returning the original state now.",
                    FUNCTION_PARAM, SOURCE_FORMAT_PARAM);
            callback.sendTimeSeries(timeSeries);
            return;
        }
        TimeSeries transformedTimeSeries = new TimeSeries(timeSeries.getPolicy());
        timeSeries.getStates()
                .forEach(entry -> transformedTimeSeries.add(entry.timestamp(), (State) transformState(entry.state())));
        callback.sendTimeSeries(transformedTimeSeries);
    }

    private Type transformState(Type state) {
        String result = state.toFullString();
        try {
            result = TransformationHelper.transform(service, function, sourceFormat, state.toFullString());
            if (result != null && result.isEmpty()) {
                // map transformation service returns an empty string if the entry is not found in the map, we will use
                // the original value
                result = state.toFullString();
            }
        } catch (TransformationException e) {
            logger.warn("Could not transform state '{}' with function '{}' and format '{}'", state, function,
                    sourceFormat);
        }
        StringType resultType = new StringType(result);
        logger.debug("Transformed '{}' into '{}'", state, resultType);
        return resultType;
    }
}
