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

import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_ALARM;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_ARMED_AWAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_ARMED_STAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_EXIT_DELAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_AREA_READY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_OUTPUT_STATE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_BATTERY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_COMMUNICATION;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_DIALER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_FIRE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_FUSE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_LINE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_MAINS;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_MEDICAL;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_PANIC;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_PENDANT_BATTERY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_RECEIVER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_SYSTEM_TAMPER;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_ALARM;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_BATTERY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_BYPASS;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_ENTRY_DELAY;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_SENSOR_WATCH;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_SUPERVISE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_TROUBLE;
import static org.openhab.binding.elitealarm.internal.EliteAlarmBindingConstants.CHANNEL_ZONE_UNSEALED;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class EliteAlarmProtocolRegistry {

    public record Definition(String signature, String subsystem, String description, Pattern pattern,
            @Nullable String channelId, BiConsumer<ProtocolMatch, String[]> mapper) {
    }

    private static final Map<String, List<Definition>> registry = new HashMap<>();

    static {
        initializeRegistry();
    }

    private static void initializeRegistry() {
        // --- ZONES ---
        registerZoneStatus("ZO", "ZC", "UNSEALED", "SEALED", CHANNEL_ZONE_UNSEALED);
        registerZoneStatus("ZT", "ZTR", "TROUBLE", "SEALED", CHANNEL_ZONE_TROUBLE);
        registerZoneStatus("ZA", "ZR", "ALARM", "SEALED", CHANNEL_ZONE_ALARM);
        registerZoneStatus("ZBY", "ZBYR", "BYPASS", "BYPASS RESTORED", CHANNEL_ZONE_BYPASS);
        registerZoneStatus("ZBL", "ZBR", "BATTERY LOW", "BATTERY RESTORED", CHANNEL_ZONE_BATTERY);
        registerZoneStatus("ZIA", "ZIR", "SENSOR WATCH ALARM", "SENSOR WATCH RESTORED", CHANNEL_ZONE_SENSOR_WATCH);
        registerZoneStatus("ZSA", "ZSR", "SUPERVISE ALARM", "SUPERVISE RESTORED", CHANNEL_ZONE_SUPERVISE);

        // --- OUTPUTS ---
        registerOutputStatus("OO", "OR", "ON", "OFF", CHANNEL_OUTPUT_STATE);

        // --- AREAS ---
        registerAreaStatus("A", "D", "AWAY", "DISARMED", CHANNEL_AREA_ARMED_AWAY, 1, 0);
        register("S", "Area", "STAY", "^S(\\d+)$", CHANNEL_AREA_ARMED_STAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = 1;
        });
        registerAreaStatus("AA", "AR", "ALARM", "DISARMED", CHANNEL_AREA_ALARM, 1, 0);
        registerAreaStatus("RO", "NR", "READY", "NOT READY", CHANNEL_AREA_READY, 1, 0);

        // Exit Delay
        register("EA", "Area", "EXIT DELAY AWAY", "^EA(\\d+)$", CHANNEL_AREA_EXIT_DELAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = 1;
        });
        register("ES", "Area", "EXIT DELAY STAY", "^ES(\\d+)$", CHANNEL_AREA_EXIT_DELAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = 1;
        });
        register("EDA", "Area", "EXIT DELAY AWAY", "^EDA(\\d+)-(\\d+)$", CHANNEL_AREA_EXIT_DELAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = 1;
            m.data1 = g[1] + "s";
        });
        register("EDS", "Area", "EXIT DELAY STAY", "^EDS(\\d+)-(\\d+)$", CHANNEL_AREA_EXIT_DELAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = 1;
            m.data1 = g[1] + "s";
        });

        // --- SYSTEM ---
        registerSystemStatus("MF", "MR", "Mains Failure", "Mains Restore", CHANNEL_SYSTEM_MAINS);
        registerSystemStatus("BF", "BR", "Battery Failure", "Battery Restore", CHANNEL_SYSTEM_BATTERY);
        registerSystemStatus("TA", "TR", "Tamper Active", "Tamper Restore", CHANNEL_SYSTEM_TAMPER);
        registerSystemStatus("FF", "FR", "Fuse Failure", "Fuse Restore", CHANNEL_SYSTEM_FUSE);
        registerSystemStatus("DF", "DR", "Dialer Failure", "Dialer Restore", CHANNEL_SYSTEM_DIALER);
        registerSystemStatus("LF", "LR", "Line Failure", "Line Restore", CHANNEL_SYSTEM_LINE);
        registerSystemStatus("CAL", "CLF", "Communication Active", "Communication Finished",
                CHANNEL_SYSTEM_COMMUNICATION);
        registerSystemStatus("RIF", "RIR", "Receiver Failure", "Receiver Restore", CHANNEL_SYSTEM_RECEIVER);
        registerSystemStatus("PA", "PC", "Panic Alarm", "Panic Clear", CHANNEL_SYSTEM_PANIC);
        registerSystemStatus("FA", "FC", "Fire Alarm", "Fire Clear", CHANNEL_SYSTEM_FIRE);
        registerSystemStatus("MA", "MC", "Medical Alarm", "Medical Clear", CHANNEL_SYSTEM_MEDICAL);

        // --- PENDANTS ---
        register("PBF", "Pendant", "Pendant Battery Low", "^PBF(\\d+)$", CHANNEL_SYSTEM_PENDANT_BATTERY, (m, g) -> {
            m.data1 = "Pendant " + g[0];
            m.output = 1;
        });
        register("PBR", "Pendant", "Pendant Battery Restored", "^PBR(\\d+)$", CHANNEL_SYSTEM_PENDANT_BATTERY,
                (m, g) -> {
                    m.data1 = "Pendant " + g[0];
                    m.output = 0;
                });

        // --- EXPANDER HEALTH ---
        registerExpanderHealth("MF", "MR", "Mains Failure", "Mains Restore", CHANNEL_SYSTEM_MAINS);
        registerExpanderHealth("TA", "TR", "Tamper Alarm", "Tamper Restore", CHANNEL_SYSTEM_TAMPER);
        registerExpanderHealth("FF", "FR", "Fuse Failure", "Fuse Restore", CHANNEL_SYSTEM_FUSE);
        registerExpanderHealth("BF", "BR", "Battery Failure", "Battery Restore", CHANNEL_SYSTEM_BATTERY);

        // --- EDGE CASES ---
        register("DU", "Area", "DISARMED", "^D(\\d+)-U(\\d+)$", CHANNEL_AREA_ARMED_AWAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.user = safeInt(g[1]);
            m.output = 0;
            m.data1 = "User " + g[1];
        });

        register("AU", "Area", "ARMED AWAY", "^A(\\d+)-U(\\d+)$", CHANNEL_AREA_ARMED_AWAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.user = safeInt(g[1]);
            m.output = 1;
            m.data1 = "User " + g[1];
        });

        register("SU", "Area", "ARMED STAY", "^S(\\d+)-U(\\d+)$", CHANNEL_AREA_ARMED_STAY, (m, g) -> {
            m.area = safeInt(g[0]);
            m.user = safeInt(g[1]);
            m.output = 1;
            m.data1 = "User " + g[1];
        });

        register("ZEDS", "Zone", "ENTRY DELAY", "^ZEDS(\\d+)-(\\d+)$", CHANNEL_ZONE_ENTRY_DELAY, (m, g) -> {
            m.zone = safeInt(g[0]);
            m.data1 = g[1] + "s";
        });

        // --- VERSION ---
        register("OKVERSION", "System", "Firmware Version", "^OK Version \"(\\S+)\\s+FW\\s+Ver\\.\\s+([^\"\\s(]+).*",
                null, (m, g) -> {
                    m.data1 = java.util.Objects.requireNonNull(g[0]); // "ECi"
                    m.data2 = java.util.Objects.requireNonNull(g[1]); // "10.3.54"
                });

        register("OKMODE", "System", "Panel Mode Query", "(?i)^OK MODE\\s+(\\d)$", null,
                (m, g) -> m.output = safeInt(g[0]));

        register("OKDEVICE", "System", "Integration Keypad ID Query", "(?i)^OK Device\\s+(\\d+).*$", null,
                (m, g) -> m.output = safeInt(g[0]));

        register("ERR", "Protocol", "Panel Command Error", "^ERR\\s+(\\d)$", null, (m, g) -> {
            Integer code = safeInt(g[0]);
            m.output = code;
            if (code == null) {
                m.data1 = "Unknown Error (Invalid Code)";
                return;
            }
            m.data1 = switch (code) {
                case 1 -> "Command not understood";
                case 2 -> "Invalid parameter";
                case 3 -> "Panel Busy";
                default -> "Unknown Error";
            };
        });

        register("OKSTATUS", "System", "Status Response", "(?i)^OK Status$", null, (m, g) -> {
        });
        register("OK", "Protocol", "Action OK", "(?i)^OK$", null, (m, g) -> {
        });
        register("WELCOME", "Protocol", "Welcome", "(?i)^Welcome$", null, (m, g) -> {
        });

        // --- AUTHENTICATION ---
        register("USERNAME", "Protocol", "User Prompt", "(?i).*user(name)?:.*", null, (m, g) -> {
        });
        register("PASSWORD", "Protocol", "Password Prompt", "(?i).*password:.*|.*pass:.*", null, (m, g) -> {
        });
    }

    private static void register(String sig, String sub, String desc, String regex, @Nullable String channelId,
            BiConsumer<ProtocolMatch, String[]> mapper) {
        List<Definition> list = java.util.Objects.requireNonNull(registry.computeIfAbsent(sig, k -> new ArrayList<>()));
        list.add(new Definition(sig, sub, desc, java.util.Objects.requireNonNull(Pattern.compile(regex)), channelId,
                mapper));
    }

    private static void registerZoneStatus(String onSig, String offSig, String onDesc, String offDesc,
            String channelId) {
        register(onSig, "Zone", onDesc, "^" + onSig + "(\\d+)$", channelId, (m, g) -> {
            m.zone = safeInt(g[0]);
            m.output = 1;
        });
        register(offSig, "Zone", offDesc, "^" + offSig + "(\\d+)$", channelId, (m, g) -> {
            m.zone = safeInt(g[0]);
            m.output = 0;
        });
    }

    private static void registerOutputStatus(String onSig, String offSig, String onDesc, String offDesc,
            String channelId) {
        register(onSig, "Output", onDesc, "^" + onSig + "(\\d+)$", channelId, (m, g) -> {
            m.outputNumber = safeInt(g[0]);
            m.output = 1;
        });
        register(offSig, "Output", offDesc, "^" + offSig + "(\\d+)$", channelId, (m, g) -> {
            m.outputNumber = safeInt(g[0]);
            m.output = 0;
        });
    }

    private static void registerAreaStatus(String sig1, String sig2, String desc1, String desc2, String channelId,
            int out1, int out2) {
        register(sig1, "Area", desc1, "^" + sig1 + "(\\d+)$", channelId, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = out1;
        });
        register(sig2, "Area", desc2, "^" + sig2 + "(\\d+)$", channelId, (m, g) -> {
            m.area = safeInt(g[0]);
            m.output = out2;
        });
    }

    private static void registerSystemStatus(String onSig, String offSig, String onDesc, String offDesc,
            String channelId) {
        register(onSig, "System", onDesc, "^" + onSig + "$", channelId, (m, g) -> m.output = 1);
        register(offSig, "System", offDesc, "^" + offSig + "$", channelId, (m, g) -> m.output = 0);
    }

    private static void registerExpanderHealth(String onSig, String offSig, String onDesc, String offDesc,
            String channelId) {
        String[] expanderTypes = { "ZX", "OX", "PX" };
        String[] expanderNames = { "InputExpander", "OutputExpander", "ProxExpander" };

        for (int i = 0; i < expanderTypes.length; i++) {
            String type = java.util.Objects.requireNonNull(expanderTypes[i]);
            String name = java.util.Objects.requireNonNull(expanderNames[i]);

            // ON
            register(onSig + type, name, onDesc + " (" + name + ")", "^" + onSig + " " + type + "(\\d+)$", channelId,
                    (m, g) -> {
                        m.expander = safeInt(g[0]);
                        m.output = 1;
                    });
            // OFF
            register(offSig + type, name, offDesc + " (" + name + ")", "^" + offSig + " " + type + "(\\d+)$", channelId,
                    (m, g) -> {
                        m.expander = safeInt(g[0]);
                        m.output = 0;
                    });
        }
    }

    private static @Nullable Integer safeInt(@Nullable String val) {
        if (val == null)
            return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("null")
    public static Optional<Definition> findMatch(String sig, String raw) {
        return Optional.ofNullable(registry.get(sig))
                .flatMap(list -> list.stream().filter(d -> d.pattern().matcher(raw).matches()).findFirst());
    }
}
