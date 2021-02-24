/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.support.creators;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;

public class LimitAndRequestCalculator {

    private static final Map<String, String> ADJACENT_UNITS_OF_MEASUREMENT = new ConcurrentHashMap<>();

    static {
        ADJACENT_UNITS_OF_MEASUREMENT.put("", "m");
        ADJACENT_UNITS_OF_MEASUREMENT.put("M", "K");
        ADJACENT_UNITS_OF_MEASUREMENT.put("G", "M");
        ADJACENT_UNITS_OF_MEASUREMENT.put("T", "G");
        ADJACENT_UNITS_OF_MEASUREMENT.put("Mi", "Ki");
        ADJACENT_UNITS_OF_MEASUREMENT.put("Gi", "Mi");
        ADJACENT_UNITS_OF_MEASUREMENT.put("Ti", "Gi");
    }

    protected DecimalFormat format = new DecimalFormat("#.###");

    protected LimitAndRequestCalculator() {
        DecimalFormatSymbols newSymbols = new DecimalFormatSymbols();
        newSymbols.setDecimalSeparator('.');
        format.setDecimalFormatSymbols(newSymbols);
    }

    protected String applyRequestRatio(String limit) {
        int i = findIndexOfUnitOfMeasurement(limit);
        double number = this.parse(limit.substring(0, i)).doubleValue();
        String uom = limit.substring(i);
        //Only integer values accepted
        double request = number * getRequestToLimitRatio();
        boolean uomIsCore = uom.length() == 0;
        if (request < 1.0D || uomIsCore) {
            //we don't like cores here. convert to millicore
            request = request * 1000;
            uom = ADJACENT_UNITS_OF_MEASUREMENT.get(uom);
        }
        return Math.round(request) + uom;
    }

    private int findIndexOfUnitOfMeasurement(String memoryLimit) {
        int i = 0;
        for (i = memoryLimit.length(); i > 0; i--) {
            if (!Character.isAlphabetic(memoryLimit.charAt(i - 1))) {
                break;
            }
        }
        return i;
    }

    protected double getRequestToLimitRatio() {
        return EntandoOperatorConfigBase.lookupProperty(
                EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO).map(this::parse).orElse(0.25D).doubleValue();
    }

    private Number parse(String source) {
        try {
            return format.parse(source);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
