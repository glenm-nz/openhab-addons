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
package org.openhab.binding.mqtt.internal.ssl;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link Pin} is either a Public Key or Certificate Pin.
 *
 * @author David Graeff - Initial contribution
 */
@NonNullByDefault
public enum PinType {
    PUBLIC_KEY_TYPE,
    CERTIFICATE_TYPE
}
