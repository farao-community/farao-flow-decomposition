/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class DecomposedFlow {
    private final Map<String, Double> decomposedFlowMap = new TreeMap<>();
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private static final String PST_COLUMN_NAME = "PST Flow";

    DecomposedFlow(Map<String, Double> decomposedFlowMap, Map<String, Double> pst) {
        this.decomposedFlowMap.putAll(decomposedFlowMap);
        this.decomposedFlowMap.put(PST_COLUMN_NAME, pst.get(PST_COLUMN_NAME));
    }

    public Double getAllocatedFlow() {
        return decomposedFlowMap.get(ALLOCATED_COLUMN_NAME);
    }

    public double getLoopFlow(Country country) {
        String columnName = NetworkUtil.getLoopFlowIdFromCountry(country);
        if (!decomposedFlowMap.containsKey(columnName)) {
            throw new PowsyblException("Country has to be present in the network");
        }
        return decomposedFlowMap.get(columnName);
    }

    public double getPstFlow() {
        return decomposedFlowMap.get(PST_COLUMN_NAME);
    }

    public String toString() {
        return decomposedFlowMap.toString();
    }

    Map<String, Double> getDecomposedFlowMap() {
        return decomposedFlowMap;
    }
}
