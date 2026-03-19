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
package org.openhab.binding.elitealarm.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A data carrier representing the structured data and metadata extracted from
 * an Elite Alarm protocol message.
 */
@NonNullByDefault
public class ProtocolMatch {
	// The Standard Data Columns
	public @Nullable Integer expander;
	public @Nullable Integer user;
	public @Nullable Integer area;
	public @Nullable Integer zone;
	public @Nullable Integer outputNumber;
	public @Nullable Integer pendant;
	public @Nullable Integer time;
	public @Nullable Integer output;

	// Generic Data Slots for metadata (Model, Version, specific IDs, etc.)
	public @Nullable String data1;
	public @Nullable String data2;

	// Metadata for Logging/Debugging
	public String signature = "";
	public String description = "";
	public String subsystem = "";
	public String rawMessage = "";

	// The target openHAB Channel ID provided by the Registry
	public @Nullable String channelId;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[").append(subsystem).append("] ").append(description);

		if (channelId != null) {
			sb.append(" (Channel: ").append(channelId).append(")");
		}

		sb.append(" (Raw: ").append(rawMessage).append(") -> ");

		if (zone != null)
			sb.append("Zone:").append(zone).append(" ");
		if (outputNumber != null)
			sb.append("OutputNumber:").append(outputNumber).append(" ");
		if (area != null)
			sb.append("Area:").append(area).append(" ");
		if (user != null)
			sb.append("User:").append(user).append(" ");
		if (expander != null)
			sb.append("Exp:").append(expander).append(" ");
		if (time != null)
			sb.append("Time:").append(time).append(" ");
		if (output != null)
			sb.append("Out:").append(output).append(" ");

		// Include the new generic data slots in logs if they contain info
		if (data1 != null)
			sb.append("Data1:").append(data1).append(" ");
		if (data2 != null)
			sb.append("Data2:").append(data2).append(" ");

		return java.util.Objects.requireNonNull(sb.toString().trim());
	}
}
