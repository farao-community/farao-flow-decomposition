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
import java.util.Set;

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
    void checkThatAllocatedFlowIsExtractedForEachXnecGivenANetwork_TestFAR670_01() {
        //String networkFileName = "20220611_2130_2D6_UX2_FEXPORTGRIDMODEL_CGM_17XTSO-CS------W.uct";
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        Network network = importNetwork(networkFileName);
        FlowDecompositionComputer allocatedFlowComputer = new FlowDecompositionComputer();
        Map<String, Map<String, Double>> allocatedFlowsMap = allocatedFlowComputer.run(network);
        assertNotNull(allocatedFlowsMap.get("FGEN1 11 BLOAD 11 1").get("Allocated"));
        assertEquals(100, allocatedFlowsMap.get("FGEN1 11 BLOAD 11 1").get("Allocated"), EPSILON);
    }
}
