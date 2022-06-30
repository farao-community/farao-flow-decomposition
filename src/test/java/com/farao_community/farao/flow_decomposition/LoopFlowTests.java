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

import static com.farao_community.farao.flow_decomposition.AllocatedFlowTests.importNetwork;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */
public class LoopFlowTests {
    private static final double EPSILON = 1e-3;

    @Test
    void checkThatLoopFlowsAreExtractedForEachXnecAndForEachCountryGivenABasicNetwork() {
        String networkFileName = "NETWORK_LOOP_FLOW_WITH_COUNTRIES.uct";

        String gBe = "BGEN  11_generator";
        String lBe = "BLOAD 11_load";
        String gFr = "FGEN  11_generator";
        String lFr = "FLOAD 11_load";
        String gEs = "EGEN  11_generator";
        String lEs = "ELOAD 11_load";

        String x1 = "EGEN  11 FGEN  11 1";
        String x2 = "FGEN  11 BGEN  11 1";
        String x4 = "BLOAD 11 FLOAD 11 1";
        String x5 = "FLOAD 11 ELOAD 11 1";

        String allocated = "Allocated";
        String cgm = "CGM";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer flowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = flowComputer.run(network, true);

        var optionalGlsks = flowDecompositionResults.getGlsks();
        assertTrue(optionalGlsks.isPresent());
        var glsks = optionalGlsks.get();
        assertEquals(1.0, glsks.get(Country.BE).get(gBe), EPSILON);
        assertEquals(1.0, glsks.get(Country.ES).get(gEs), EPSILON);
        assertEquals(1.0, glsks.get(Country.FR).get(gFr), EPSILON);

        var optionalPtdfs = flowDecompositionResults.getPtdfMap();
        assertTrue(optionalPtdfs.isPresent());

        var optionalCGMNodalInjections = flowDecompositionResults.getReferenceNodalInjectionsMap();
        assertTrue(optionalCGMNodalInjections.isPresent());
        var cgmNodalInjections = optionalCGMNodalInjections.get();
        assertEquals( 100, cgmNodalInjections.get(gBe).get(cgm));
        assertEquals( 100, cgmNodalInjections.get(gEs).get(cgm));
        assertEquals( 100, cgmNodalInjections.get(gFr).get(cgm));
        assertEquals(-100, cgmNodalInjections.get(lBe).get(cgm));
        assertEquals(-100, cgmNodalInjections.get(lEs).get(cgm));
        assertEquals(-100, cgmNodalInjections.get(lFr).get(cgm));

        var optionalNodalInjections = flowDecompositionResults.getNodalInjectionsMap();
        assertTrue(optionalNodalInjections.isPresent());
        var nodalInjections = optionalNodalInjections.get();
        assertEquals(0, nodalInjections.get(gBe).get(allocated));
        assertEquals(0, nodalInjections.get(gEs).get(allocated));
        assertEquals(0, nodalInjections.get(gFr).get(allocated));
        assertEquals(0, nodalInjections.get(lBe).get(allocated));
        assertEquals(0, nodalInjections.get(lEs).get(allocated));
        assertEquals(0, nodalInjections.get(lFr).get(allocated));
        assertEquals(0, nodalInjections.get(gBe).get(Country.BE.toString()));
        assertEquals(0, nodalInjections.get(gEs).get(Country.ES.toString()));
        assertEquals(0, nodalInjections.get(gFr).get(Country.FR.toString()));
        assertEquals(0, nodalInjections.get(lBe).get(Country.BE.toString()));
        assertEquals(0, nodalInjections.get(lEs).get(Country.ES.toString()));
        assertEquals(0, nodalInjections.get(lFr).get(Country.FR.toString()));

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        assertEquals(  0, decomposedFlowMap.get(x1).getAllocatedFlow(), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x2).getAllocatedFlow(), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x4).getAllocatedFlow(), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x5).getAllocatedFlow(), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x1).getLoopFlow(Country.BE), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x1).getLoopFlow(Country.ES), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x1).getLoopFlow(Country.FR), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x2).getLoopFlow(Country.BE), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x2).getLoopFlow(Country.ES), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x2).getLoopFlow(Country.FR), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x4).getLoopFlow(Country.BE), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x4).getLoopFlow(Country.ES), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x4).getLoopFlow(Country.FR), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x5).getLoopFlow(Country.BE), EPSILON);
        assertEquals(100, decomposedFlowMap.get(x5).getLoopFlow(Country.ES), EPSILON);
        assertEquals(  0, decomposedFlowMap.get(x5).getLoopFlow(Country.FR), EPSILON);
    }
}
