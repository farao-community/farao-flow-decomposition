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

    private Network importNetwork(String networkResourcePath) {
        String networkName = Paths.get(networkResourcePath).getFileName().toString();
        return Importers.loadNetwork(networkName, getClass().getResourceAsStream(networkResourcePath));
    }

    @Test
    void checkThatAllocatedFlowAreExtractedForEachXnecGivenANetwork() {
        //String networkFileName = "20220611_2130_2D6_UX2_FEXPORTGRIDMODEL_CGM_17XTSO-CS------W.uct";
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        String gen_be = "BGEN2 11_generator";
        String load_be = "BLOAD 11_load";
        String gen_fr = "FGEN1 11_generator";
        String xnec_fr_be = "FGEN1 11 BLOAD 11 1";
        String allocated = "Allocated";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);

        Map<String, Map<String, Double>> allocatedFlowsMap = flowDecompositionResults.getAllocatedFlowsMatrix().toMap();
        assertEquals(100, allocatedFlowsMap.get(xnec_fr_be).get(allocated), EPSILON);

        IntermediateFlowDecompositionResults intermediateResults = flowDecompositionResults.getIntermediateResults();
        Map<Country, Map<String, Double>> glsks = intermediateResults.getGlsks();
        assertEquals(1.0, glsks.get(Country.FR).get(gen_fr), EPSILON);
        assertEquals(1.0, glsks.get(Country.BE).get(gen_be), EPSILON);

        Map<String, Map<String, Double>> ptdfMatrix = intermediateResults.getPtdfMap();
        assertEquals(-0.5, ptdfMatrix.get(xnec_fr_be).get(load_be));
        assertEquals(-0.5, ptdfMatrix.get(xnec_fr_be).get(gen_be));
        assertEquals(+0.5, ptdfMatrix.get(xnec_fr_be).get(gen_fr));

        Map<String, Map<String, Double>> nodalInjection = intermediateResults.getNodalInjectionsMap();
        assertEquals(-100, nodalInjection.get(gen_be).get(allocated));
        assertEquals(+100, nodalInjection.get(gen_fr).get(allocated));

        System.out.println("done");
    }
}
