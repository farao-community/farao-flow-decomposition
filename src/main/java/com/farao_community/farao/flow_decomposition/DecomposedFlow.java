/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;

import java.util.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class DecomposedFlow {
    public static final double DEFAULT_FLOW = 0.;
    private final Map<String, Double> decomposedFlowMap = new TreeMap<>();
    private final double acReferenceFlow;
    private final double dcReferenceFlow;
    static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    static final String PST_COLUMN_NAME = "PST Flow";
    static final String AC_REFERENCE_FLOW_COLUMN_NAME = "Reference AC Flow";
    static final String DC_REFERENCE_FLOW_COLUMN_NAME = "Reference DC Flow";

    DecomposedFlow(Map<String, Double> decomposedFlowMap, Map<String, Double> pst, double acReferenceFlow, double dcReferenceFlow) {
        this.decomposedFlowMap.putAll(decomposedFlowMap);
        this.decomposedFlowMap.put(PST_COLUMN_NAME, pst.get(PST_COLUMN_NAME));
        this.acReferenceFlow = acReferenceFlow;
        this.dcReferenceFlow = dcReferenceFlow;
    }

    DecomposedFlow(DecomposedFlow decomposedFlow) {
        this.decomposedFlowMap.putAll(decomposedFlow.decomposedFlowMap);
        this.acReferenceFlow = decomposedFlow.getAcReferenceFlow();
        this.dcReferenceFlow = decomposedFlow.getDcReferenceFlow();
    }

    public double getAllocatedFlow() {
        return get(ALLOCATED_COLUMN_NAME);
    }

    public double getLoopFlow(Country country) {
        return get(NetworkUtil.getLoopFlowIdFromCountry(country));
    }

    public double getPstFlow() {
        return get(PST_COLUMN_NAME);
    }

    public double getAcReferenceFlow() {
        return acReferenceFlow;
    }

    public double getDcReferenceFlow() {
        return dcReferenceFlow;
    }

    double getTotalFlow() {
        return decomposedFlowMap.values().stream()
            .reduce(0., Double::sum);
    }

    double getReferenceOrientedTotalFlow() {
        return getTotalFlow() * Math.signum(getAcReferenceFlow());
    }

    public String toString() {
        return getAllKeyMap().toString();
    }

    Set<String> allKeySet() {
        return getAllKeyMap().keySet();
    }

    Set<String> keySet() {
        return decomposedFlowMap.keySet();
    }

    void put(String key, double value) {
        decomposedFlowMap.put(key, value);
    }

    double get(String key) {
        return decomposedFlowMap.getOrDefault(key,
            getAllKeyMap().getOrDefault(key, DEFAULT_FLOW));
    }

    private TreeMap<String, Double> getAllKeyMap() {
        TreeMap<String, Double> localDecomposedFlowMap = new TreeMap<>(decomposedFlowMap);
        localDecomposedFlowMap.put(AC_REFERENCE_FLOW_COLUMN_NAME, getAcReferenceFlow());
        localDecomposedFlowMap.put(DC_REFERENCE_FLOW_COLUMN_NAME, getDcReferenceFlow());
        return localDecomposedFlowMap;
    }
}
