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
package org.openhab.binding.elitealarm.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link EliteAlarmBindingConstants} class defines common constants used
 * across the Elite Alarm binding. These constants link the Java Handler logic
 * with the XML Thing and Channel definitions.
 *
 * @author Glen McGeachen - Initial contribution with input from Gemini
 */
@NonNullByDefault
public class EliteAlarmBindingConstants {

	public static final String BINDING_ID = "elitealarm";

	// Thing Types
	public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
	public static final ThingTypeUID THING_TYPE_ZONE = new ThingTypeUID(BINDING_ID, "zone");
	public static final ThingTypeUID THING_TYPE_AREA = new ThingTypeUID(BINDING_ID, "area");
	public static final ThingTypeUID THING_TYPE_OUTPUT = new ThingTypeUID(BINDING_ID, "output");
	public static final ThingTypeUID THING_TYPE_SYSTEM = new ThingTypeUID(BINDING_ID, "system");
	public static final ThingTypeUID THING_TYPE_INPUT_EXPANDER = new ThingTypeUID(BINDING_ID, "input_expander");
	public static final ThingTypeUID THING_TYPE_OUTPUT_EXPANDER = new ThingTypeUID(BINDING_ID, "output_expander");
	public static final ThingTypeUID THING_TYPE_PROX_EXPANDER = new ThingTypeUID(BINDING_ID, "prox_expander");

	// Bridge Configuration Parameters
	public static final String CONFIG_HOST = "host";
	public static final String CONFIG_PORT = "port";
	public static final String CONFIG_REFRESH = "refreshInterval";
	public static final String CONFIG_RECONNECT = "reconnectInterval";
	public static final String CONFIG_USERNAME = "username";
	public static final String CONFIG_PASSWORD = "password";
	public static final String CONFIG_ZONE_NUMBER = "zoneNumber";
	public static final String CONFIG_AREA_NUMBER = "areaNumber";
	public static final String CONFIG_OUTPUT_NUMBER = "outputNumber";
	public static final String CONFIG_EXPANDER_NUMBER = "expanderNumber";

	// Zone Channels
	public static final String CHANNEL_ZONE_STATUS = "status";
	public static final String CHANNEL_ZONE_UNSEALED = "unsealed";
	public static final String CHANNEL_ZONE_ALARM = "alarm";
	public static final String CHANNEL_ZONE_TROUBLE = "trouble";
	public static final String CHANNEL_ZONE_BYPASS = "bypass";
	public static final String CHANNEL_ZONE_BATTERY = "battery";
	public static final String CHANNEL_ZONE_SUPERVISE = "supervise";
	public static final String CHANNEL_ZONE_SENSOR_WATCH = "sensor-watch";
	public static final String CHANNEL_ZONE_ENTRY_DELAY = "entry-delay";

	// Area Channels
	public static final String CHANNEL_AREA_ARMED_AWAY = "armed-away";
	public static final String CHANNEL_AREA_ARMED_STAY = "armed-stay";
	public static final String CHANNEL_AREA_ALARM = "alarm";
	public static final String CHANNEL_AREA_READY = "ready";
	public static final String CHANNEL_AREA_STATUS = "area-status";
	public static final String CHANNEL_AREA_LAST_USER = "last-user";
	public static final String CHANNEL_AREA_EXIT_DELAY = "exit-delay";

	// Output Channels
	public static final String CHANNEL_OUTPUT_STATE = "state";

	// System Health Channels
	public static final String CHANNEL_SYSTEM_MAINS = "mains-trouble";
	public static final String CHANNEL_SYSTEM_BATTERY = "battery-trouble";
	public static final String CHANNEL_SYSTEM_TAMPER = "tamper-trouble";
	public static final String CHANNEL_SYSTEM_EXPANDER = "expander-trouble";
	public static final String CHANNEL_SYSTEM_FUSE = "fuse-trouble";
	public static final String CHANNEL_SYSTEM_RECEIVER = "receiver-trouble";
	public static final String CHANNEL_SYSTEM_DIALER = "dialer-trouble";
	public static final String CHANNEL_SYSTEM_LINE = "line-trouble";
	public static final String CHANNEL_SYSTEM_COMMUNICATION = "communication-trouble";
	public static final String CHANNEL_SYSTEM_PANIC = "panic-alarm";
	public static final String CHANNEL_SYSTEM_FIRE = "fire-alarm";
	public static final String CHANNEL_SYSTEM_MEDICAL = "medical-alarm";
	public static final String CHANNEL_SYSTEM_PENDANT_BATTERY = "pendant-battery-trouble";
	public static final String CHANNEL_SYSTEM_RAW_COMMAND = "raw-command";

	/**
	 * All Thing Types supported by this binding. Used for discovery and registry
	 * checks.
	 */
	@SuppressWarnings("null")
	public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_BRIDGE, THING_TYPE_ZONE,
			THING_TYPE_AREA, THING_TYPE_OUTPUT, THING_TYPE_SYSTEM, THING_TYPE_INPUT_EXPANDER,
			THING_TYPE_OUTPUT_EXPANDER, THING_TYPE_PROX_EXPANDER);
}
