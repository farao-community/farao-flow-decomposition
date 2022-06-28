/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */

class AllocatedFlowTests {
    private static final double EPSILON = 1e-3;

    static Network importNetwork(String networkResourcePath) {
        String networkName = Paths.get(networkResourcePath).getFileName().toString();
        return Importers.loadNetwork(networkName, AllocatedFlowTests.class.getResourceAsStream(networkResourcePath));
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenABasicNetwork() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        String genBe = "BGEN2 11_generator";
        String loadBe = "BLOAD 11_load";
        String genFr = "FGEN1 11_generator";
        String xnecFrBee = "FGEN1 11 BLOAD 11 1";
        String allocated = "Allocated";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        assertEquals(100, decomposedFlowMap.get(xnecFrBee).getAllocatedFlow(), EPSILON);

        var optionalGlsks = flowDecompositionResults.getGlsks();
        assertTrue(optionalGlsks.isPresent());
        var glsks = optionalGlsks.get();
        assertEquals(1.0, glsks.get(Country.FR).get(genFr), EPSILON);
        assertEquals(1.0, glsks.get(Country.BE).get(genBe), EPSILON);

        var optionalPtdfs = flowDecompositionResults.getPtdfMap();
        assertTrue(optionalPtdfs.isPresent());
        var ptdfs = optionalPtdfs.get();
        assertEquals(-0.5, ptdfs.get(xnecFrBee).get(loadBe));
        assertEquals(-0.5, ptdfs.get(xnecFrBee).get(genBe));
        assertEquals(+0.5, ptdfs.get(xnecFrBee).get(genFr));

        var optionalNodalInjections = flowDecompositionResults.getNodalInjectionsMap();
        assertTrue(optionalNodalInjections.isPresent());
        var nodalInjections = optionalNodalInjections.get();
        assertEquals(-100, nodalInjections.get(genBe).get(allocated));
        assertEquals(+100, nodalInjections.get(genFr).get(allocated));
    }

    @Test
    void checkThatFlowDecompositionDoesNotExtractIntermediateResultsByDefault() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network);
        assertTrue(flowDecompositionResults.getGlsks().isEmpty());
        assertTrue(flowDecompositionResults.getPtdfMap().isEmpty());
        assertTrue(flowDecompositionResults.getNodalInjectionsMap().isEmpty());
    }

}
