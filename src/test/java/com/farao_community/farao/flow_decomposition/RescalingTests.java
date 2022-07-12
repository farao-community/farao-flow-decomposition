/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class RescalingTests {
    private static final String ALLOCATED_COLUMN_NAME = "Allocated Flow";
    private static final String PST_COLUMN_NAME = "PST Flow";
    private static final double EPSILON = 1e-5;

    @Test
    void testAcerNormalizationWithPositiveSmallerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 50.0);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -50.0);
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 300.0);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 50.0);
        double acReferenceFlow = 200.0;
        double dcReferenceFlow = 350.0;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        rescaleSmallerFlows(acReferenceFlow, dcReferenceFlow, decomposedFlow);
    }

    @Test
    void testAcerNormalizationWithNegativeSmallerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 50.0);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -50.0);
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 300.0);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 50.0);
        double acReferenceFlow = -200.0;
        double dcReferenceFlow = -350.0;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        rescaleSmallerFlows(acReferenceFlow, dcReferenceFlow, decomposedFlow);
    }

    private void rescaleSmallerFlows(double acReferenceFlow, double dcReferenceFlow, DecomposedFlow decomposedFlow) {
        DecomposedFlowRescaler rescaler = new DecomposedFlowRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(150, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(25, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(0.0, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(25, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }

    @Test
    void testAcerNormalizationWithPositiveBiggerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 50.0);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -50.0);
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 300.0);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 50.0);
        double acReferenceFlow = 800.0;
        double dcReferenceFlow = 350.0;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        rescaleBiggerFlows(acReferenceFlow, dcReferenceFlow, decomposedFlow);
    }

    @Test
    void testAcerNormalizationWithNegativeBiggerReferenceFlows() {
        Map<String, Double> allocatedAndLoopFlows = new TreeMap<>();
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.BE), 50.0);
        allocatedAndLoopFlows.put(NetworkUtil.getLoopFlowIdFromCountry(Country.FR), -50.0);
        allocatedAndLoopFlows.put(ALLOCATED_COLUMN_NAME, 300.0);
        Map<String, Double> pstFlow = Map.of(PST_COLUMN_NAME, 50.0);
        double acReferenceFlow = -800.0;
        double dcReferenceFlow = -350.0;
        DecomposedFlow decomposedFlow = new DecomposedFlow(allocatedAndLoopFlows, pstFlow, acReferenceFlow, dcReferenceFlow);
        rescaleBiggerFlows(acReferenceFlow, dcReferenceFlow, decomposedFlow);
    }

    private void rescaleBiggerFlows(double acReferenceFlow, double dcReferenceFlow, DecomposedFlow decomposedFlow) {
        DecomposedFlowRescaler rescaler = new DecomposedFlowRescaler();
        DecomposedFlow rescaledFlow = rescaler.rescale(decomposedFlow);
        assertEquals(dcReferenceFlow, decomposedFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getReferenceOrientedTotalFlow(), EPSILON);
        assertEquals(600, rescaledFlow.getAllocatedFlow(), EPSILON);
        assertEquals(100, rescaledFlow.getLoopFlow(Country.BE), EPSILON);
        assertEquals(0.0, rescaledFlow.getLoopFlow(Country.FR), EPSILON);
        assertEquals(100, rescaledFlow.getPstFlow(), EPSILON);
        assertEquals(acReferenceFlow, rescaledFlow.getAcReferenceFlow(), EPSILON);
        assertEquals(dcReferenceFlow, rescaledFlow.getDcReferenceFlow(), EPSILON);
    }
}
