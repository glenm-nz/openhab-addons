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
package org.openhab.binding.lametrictime.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lametrictime.internal.api.local.dto.Widget;

/**
 * The {@link LaMetricTimeAppHandler} provides a common contract for all app handlers available for the device.
 *
 * @author Gregory Moyer - Initial contribution
 */
@NonNullByDefault
public interface LaMetricTimeAppHandler {

    /**
     * Retrieve the LaMetric Time app instance ({@link Widget}) associated with this handler.
     *
     * @return the {@link Widget}
     */
    @Nullable
    Widget getWidget();
}
