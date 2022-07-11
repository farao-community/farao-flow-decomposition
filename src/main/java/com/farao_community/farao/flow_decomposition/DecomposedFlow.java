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
 * This class provides a flow decomposition interface for a single XNEC.
 *
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class DecomposedFlow {
    private final Map<String, Double> decomposedFlowMap = new TreeMap<>();
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private static final String PST_COLUMN_NAME = "PST Flow";
    private static final String AC_REFERENCE_FLOW_COLUMN_NAME = "Reference AC Flow";
    private static final String DC_REFERENCE_FLOW_COLUMN_NAME = "Reference DC Flow";

    DecomposedFlow(Map<String, Double> decomposedFlowMap, Map<String, Double> pst, Double acReferenceFlow, Double dcReferenceFlow) {
        this.decomposedFlowMap.putAll(decomposedFlowMap);
        this.decomposedFlowMap.put(PST_COLUMN_NAME, pst.get(PST_COLUMN_NAME));
        this.decomposedFlowMap.put(AC_REFERENCE_FLOW_COLUMN_NAME, acReferenceFlow);
        this.decomposedFlowMap.put(DC_REFERENCE_FLOW_COLUMN_NAME, dcReferenceFlow);
    }

    DecomposedFlow(DecomposedFlow decomposedFlow) {
        this.decomposedFlowMap.putAll(decomposedFlow.decomposedFlowMap);
    }

    /**
     * @return the allocated flow of the XNEC.
     */
    public Double getAllocatedFlow() {
        return decomposedFlowMap.get(ALLOCATED_COLUMN_NAME);
    }

    /**
     * @param country the origin country of the loop flow
     * @return the loop flow of country on the XNEC
     */
    public double getLoopFlow(Country country) {
        String columnName = NetworkUtil.getLoopFlowIdFromCountry(country);
        if (!decomposedFlowMap.containsKey(columnName)) {
            throw new PowsyblException("Country has to be present in the network");
        }
        return decomposedFlowMap.get(columnName);
    }

    /**
     * @return the PST flow on the XNEC
     */
    public double getPstFlow() {
        return decomposedFlowMap.get(PST_COLUMN_NAME);
    }

    /**
     * @return The AC reference flow on the XNEC
     */
    public double getAcReferenceFlow() {
        return decomposedFlowMap.get(AC_REFERENCE_FLOW_COLUMN_NAME);
    }

    /**
     * @return The DC reference flow on the XNEC
     */
    public double getDcReferenceFlow() {
        return decomposedFlowMap.get(DC_REFERENCE_FLOW_COLUMN_NAME);
    }

    public String toString() {
        return decomposedFlowMap.toString();
    }

    Map<String, Double> getDecomposedFlowMap() {
        return decomposedFlowMap;
    }

    Double getTotalFlow() {
        return decomposedFlowMap.entrySet().stream()
            .filter(this::isNotAReferenceEntry)
            .mapToDouble(Map.Entry::getValue)
            .sum() * Math.signum(decomposedFlowMap.get(AC_REFERENCE_FLOW_COLUMN_NAME));
    }

    private boolean isNotAReferenceEntry(Map.Entry<String, Double> stringDoubleEntry) {
        return isNotAReferenceColumn(stringDoubleEntry.getKey());
    }

    void replaceRelievingFlows() {
        decomposedFlowMap.keySet().stream()
            .filter(this::isNotAReferenceColumn)
            .forEach(column -> decomposedFlowMap.put(column, reLU(decomposedFlowMap.get(column))));
    }

    private Boolean isReferenceColumn(String column) {
        return column.equals(AC_REFERENCE_FLOW_COLUMN_NAME) || column.equals(DC_REFERENCE_FLOW_COLUMN_NAME);
    }

    private Boolean isNotAReferenceColumn(String column) {
        return !isReferenceColumn(column);
    }

    private Double reLU(Double value) {
        if (value < 0) {
            return 0.0;
        }
        return value;
    }

    void scale(double coefficient) {
        decomposedFlowMap.keySet().stream()
            .filter(this::isNotAReferenceColumn)
            .forEach(column -> decomposedFlowMap.put(column, decomposedFlowMap.get(column) * coefficient));
    }
}
