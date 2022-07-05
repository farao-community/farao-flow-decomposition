/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

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
public class PstFlowTests {
    private static final double EPSILON = 1e-3;
    private static final String PST_COLUMN_NAME = "PST";

    @Test
    void checkThatPSTFlowsAreExtractedForEachXnecAndForEachPSTGivenABasicNetworkWithNeutralTap() {
        String networkFileName = "NETWORK_PST_FLOW_WITH_COUNTRIES.uct";

        String lBe = "BLOAD 11_load";
        String gBe = "BLOAD 11_generator";
        String gFr = "FGEN  11_generator";
        String pst = "BLOAD 11 BLOAD 12 2";

        String x1 = "FGEN  11 BLOAD 11 1";
        String x2 = "FGEN  11 BLOAD 12 1";

        String allocated = "Allocated";
        String cgm = "CGM";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer flowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = flowComputer.run(network, true);

        var optionalPsdfs = flowDecompositionResults.getPsdfMap();
        assertTrue(optionalPsdfs.isPresent());
        var psdf = optionalPsdfs.get();
        assertEquals(-420.042573, psdf.get(x1).get(pst), EPSILON);
        assertEquals(420.042573, psdf.get(x2).get(pst), EPSILON);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap(true);
        assertEquals(0, decomposedFlowMap.get(x1).getPstFlow(pst), EPSILON);
        assertEquals(0, decomposedFlowMap.get(x2).getPstFlow(pst), EPSILON);
    }

    @Test
    void checkThatPSTFlowsAreExtractedForEachXnecAndForEachPSTGivenABasicNetworkWithNonNeutralTap() {
        String networkFileName = "NETWORK_PST_FLOW_WITH_COUNTRIES_NON_NEUTRAL.uct";

        String lBe = "BLOAD 11_load";
        String gBe = "BLOAD 11_generator";
        String gFr = "FGEN  11_generator";
        String pst = "BLOAD 11 BLOAD 12 2";

        String x1 = "FGEN  11 BLOAD 11 1";
        String x2 = "FGEN  11 BLOAD 12 1";

        String allocated = "Allocated";
        String cgm = "CGM";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer flowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = flowComputer.run(network, true);

        var optionalPsdfs = flowDecompositionResults.getPsdfMap();
        assertTrue(optionalPsdfs.isPresent());
        var psdf = optionalPsdfs.get();
        assertEquals(-420.042573, psdf.get(x1).get(pst), EPSILON);
        assertEquals(-420.042573, psdf.get(x2).get(pst), EPSILON);

        Map<String, Map<String, Double>> pstFlowMap = flowDecompositionResults.getPstFlowsMap(true);
        assertEquals(163.652702605, pstFlowMap.get(x1).get(PST_COLUMN_NAME), EPSILON);
        assertEquals(163.652702605, pstFlowMap.get(x2).get(PST_COLUMN_NAME), EPSILON);
    }
}
