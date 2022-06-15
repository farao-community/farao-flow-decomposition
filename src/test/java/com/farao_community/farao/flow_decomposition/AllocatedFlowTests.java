/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 * @author Hugo Schindler {@literal <hugo.schindler at rte-france.com>}
 */

class AllocatedFlowTests {
    private static final double EPSILON = 1e-3;

    @Test
    void checkThatBasicNetworkIsWellImported() {
        String networkFileName = "NETWORK_SINGLE_LOAD_TWO_GENERATORS_WITH_COUNTRIES.uct";
        NetworkInterface networkInterface = new NetworkInterface(networkFileName);
        Network basicNetwork = networkInterface.getNetwork();
        Branch<?> branchWithGeneratorOnTerminal1 = basicNetwork.getBranch("FGEN1 11 BLOAD 11 1");
        Branch<?> branchWithGeneratorOnTerminal2 = basicNetwork.getBranch("BLOAD 11 BGEN2 11 1");
        Generator generatorFromBranch1 = basicNetwork.getGenerator("FGEN1 11_generator");
        Generator generatorFromBranch2 = basicNetwork.getGenerator("BGEN2 11_generator");
        Load centralLoad = basicNetwork.getLoad("BLOAD 11_load");
        assertNotNull(branchWithGeneratorOnTerminal1);
        assertNotNull(branchWithGeneratorOnTerminal2);
        assertNotNull(generatorFromBranch1);
        assertNotNull(generatorFromBranch2);
        assertNotNull(centralLoad);
    }
}
