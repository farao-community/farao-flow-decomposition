/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
class RescalingTests {
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private static final String PST_COLUMN_NAME = "PST Flow";
    private static final double EPSILON = 1e-5;

    @Test
    void testAcerNormalizationWithPositiveBiggerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 100.);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 200.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 500.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -300.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.GE), -100.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.ES), 700.);
        double acReferenceFlow = 1400.;
        double dcReferenceFlow = 1100.;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);

        DecompositionRescaler rescaler = new DecompositionRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(120, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(240, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(600, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(-300, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(-100, rescaledFlow.getLoopFlow(Country.GE), EPSILON);
        assertEquals(840, rescaledFlow.getLoopFlow(Country.ES), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }

    @Test
    void testAcerNormalizationWithPositiveSmallerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 100.);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 200.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 500.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -300.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.GE), -100.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.ES), 700.);
        double acReferenceFlow = 800.;
        double dcReferenceFlow = 1100.;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);

        DecompositionRescaler rescaler = new DecompositionRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(80, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(160, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(400, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(-300, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(-100, rescaledFlow.getLoopFlow(Country.GE), EPSILON);
        assertEquals(560, rescaledFlow.getLoopFlow(Country.ES), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }

    @Test
    void testAcerNormalizationWithNegativeBiggerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 100.);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 200.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 500.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -300.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.GE), -100.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.ES), 700.);
        double acReferenceFlow = -1400.;
        double dcReferenceFlow = -1100.;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);

        DecompositionRescaler rescaler = new DecompositionRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(120, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(240, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(600, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(-300, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(-100, rescaledFlow.getLoopFlow(Country.GE), EPSILON);
        assertEquals(840, rescaledFlow.getLoopFlow(Country.ES), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }

    @Test
    void testAcerNormalizationWithNegativeSmallerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 100.);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 200.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 500.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -300.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.GE), -100.);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.ES), 700.);
        double acReferenceFlow = -800.;
        double dcReferenceFlow = -1100.;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);

        DecompositionRescaler rescaler = new DecompositionRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(80, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(160, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(400, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(-300, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(-100, rescaledFlow.getLoopFlow(Country.GE), EPSILON);
        assertEquals(560, rescaledFlow.getLoopFlow(Country.ES), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }

    @Test
    void testNormalizationWithFlowDecompositionResults() {
        String networkFileName = "NETWORK_PST_FLOW_WITH_COUNTRIES.uct";
        Network network = AllocatedFlowTests.importNetwork(networkFileName);

        FlowDecompositionParameters flowDecompositionParameters = new FlowDecompositionParameters();
        flowDecompositionParameters.enableLossesCompensation(FlowDecompositionParameters.ENABLE_LOSSES_COMPENSATION);
        flowDecompositionParameters.setLossesCompensationEpsilon(FlowDecompositionParameters.NO_LOSSES_COMPENSATION_EPSILON);
        flowDecompositionParameters.setSensitivityEpsilon(FlowDecompositionParameters.NO_SENSITIVITY_EPSILON);
        flowDecompositionParameters.setEnableExportRescaled(FlowDecompositionParameters.DISABLE_EXPORT_RESCALED_RESULTS);

        FlowDecompositionComputer flowDecompositionComputer = new FlowDecompositionComputer(flowDecompositionParameters);
        FlowDecompositionResults flowDecompositionResults = flowDecompositionComputer.run(network, true);
        DecomposedFlow decomposedFlow = getFirstDecomposedFlow(flowDecompositionResults);
        assertEquals(decomposedFlow.getDcReferenceFlow(), decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);

        DecompositionRescaler rescaler = new DecompositionRescaler();
        FlowDecompositionResults rescaledFlowDecompositionResults = rescaler.rescale(flowDecompositionResults);
        DecomposedFlow rescaledDecomposedFlow = getFirstDecomposedFlow(rescaledFlowDecompositionResults);

        assertEquals(rescaledDecomposedFlow.getAcReferenceFlow(), rescaledDecomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);
    }

    private DecomposedFlow getFirstDecomposedFlow(FlowDecompositionResults flowDecompositionResults) {
        return flowDecompositionResults.getDecomposedFlowsMap().values().iterator().next();
    }
}
