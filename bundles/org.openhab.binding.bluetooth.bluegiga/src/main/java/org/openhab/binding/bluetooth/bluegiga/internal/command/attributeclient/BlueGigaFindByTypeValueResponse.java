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
package org.openhab.binding.bluetooth.bluegiga.internal.command.attributeclient;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.bluetooth.bluegiga.internal.BlueGigaDeviceResponse;
import org.openhab.binding.bluetooth.bluegiga.internal.enumeration.BgApiResponse;

/**
 * Class to implement the BlueGiga command <b>findByTypeValue</b>.
 * <p>
 * This command can be used to find specific attributes on a remote device based on their 16-bit
 * UUID value and value. The search can be limited by a starting and ending handle values.
 * <p>
 * This class provides methods for processing BlueGiga API commands.
 * <p>
 * Note that this code is autogenerated. Manual changes may be overwritten.
 *
 * @author Chris Jackson - Initial contribution of Java code generator
 */
@NonNullByDefault
public class BlueGigaFindByTypeValueResponse extends BlueGigaDeviceResponse {
    public static final int COMMAND_CLASS = 0x04;
    public static final int COMMAND_METHOD = 0x00;

    /**
     * 0 : the operation was successful. Otherwise error occurred
     * <p>
     * BlueGiga API type is <i>BgApiResponse</i> - Java type is {@link BgApiResponse}
     */
    private BgApiResponse result;

    /**
     * Response constructor
     */
    public BlueGigaFindByTypeValueResponse(int[] inputBuffer) {
        // Super creates deserializer and reads header fields
        super(inputBuffer);

        event = (inputBuffer[0] & 0x80) != 0;

        // Deserialize the fields
        connection = deserializeUInt8();
        result = deserializeBgApiResponse();
    }

    /**
     * 0 : the operation was successful. Otherwise error occurred
     * <p>
     * BlueGiga API type is <i>BgApiResponse</i> - Java type is {@link BgApiResponse}
     *
     * @return the current result as {@link BgApiResponse}
     */
    public BgApiResponse getResult() {
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BlueGigaFindByTypeValueResponse [connection=");
        builder.append(connection);
        builder.append(", result=");
        builder.append(result);
        builder.append(']');
        return builder.toString();
    }
}
