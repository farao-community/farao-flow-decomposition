package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Network;

public class GlskInterface {
    public void getAutoGlsk(Network network) {
        network.getSubstations();
    }
}
