/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

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
        String gen_be = "BGEN2 11_generator";
        String load_be = "BLOAD 11_load";
        String gen_fr = "FGEN1 11_generator";
        String xnec_fr_be = "FGEN1 11 BLOAD 11 1";
        String allocated = "Allocated";

        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        FlowDecompositionResults flowDecompositionResults = allocatedFlowComputer.run(network, true);

        Map<String, DecomposedFlow> decomposedFlowMap = flowDecompositionResults.getDecomposedFlowsMap();
        assertEquals(100, decomposedFlowMap.get(xnec_fr_be).getAllocatedFlow(), EPSILON);

        Map<Country, Map<String, Double>> glsks = flowDecompositionResults.getGlsks();
        assertEquals(1.0, glsks.get(Country.FR).get(gen_fr), EPSILON);
        assertEquals(1.0, glsks.get(Country.BE).get(gen_be), EPSILON);

        Map<String, Map<String, Double>> ptdfs = flowDecompositionResults.getPtdfMap();
        assertEquals(-0.5, ptdfs.get(xnec_fr_be).get(load_be));
        assertEquals(-0.5, ptdfs.get(xnec_fr_be).get(gen_be));
        assertEquals(+0.5, ptdfs.get(xnec_fr_be).get(gen_fr));

        Map<String, Map<String, Double>> nodalInjection = flowDecompositionResults.getNodalInjectionsMap();
        assertEquals(-100, nodalInjection.get(gen_be).get(allocated));
        assertEquals(+100, nodalInjection.get(gen_fr).get(allocated));
    }

}
