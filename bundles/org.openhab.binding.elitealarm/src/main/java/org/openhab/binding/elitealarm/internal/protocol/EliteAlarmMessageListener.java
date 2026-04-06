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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface for listening to parsed messages from the Elite Alarm panel.
 */
@NonNullByDefault
public interface EliteAlarmMessageListener {
    /**
     * The centralized entry point for all structured protocol matches.
     */
    void onMessageReceived(ProtocolMatch match);

    /**
     * Called when the TCP connection state changes.
     */
    void onConnectionStateChanged(boolean online);

    /**
     * Called when the connection is lost unexpectedly.
     */
    void onConnectionLost();
}
