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

package org.entando.kubernetes.controller.common;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;

public class LimitAndRequestCalculator {

    protected DecimalFormat format = new DecimalFormat("#.###");

    protected LimitAndRequestCalculator() {
        DecimalFormatSymbols newSymbols = new DecimalFormatSymbols();
        newSymbols.setDecimalSeparator('.');
        format.setDecimalFormatSymbols(newSymbols);
    }

    protected String applyRequestRatio(String memoryLimit) {
        int i = findIndexOfUnitOfMeasurement(memoryLimit);
        double number = this.parse(memoryLimit.substring(0, i)).doubleValue();
        String uom = memoryLimit.substring(i);
        return format.format(number * getRequestToLimitRatio()) + uom;
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
        return EntandoOperatorConfig.lookupProperty(
                EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO).map(this::parse).orElse(0.1d).doubleValue();
    }

    private Number parse(String source) {
        try {
            return format.parse(source);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
