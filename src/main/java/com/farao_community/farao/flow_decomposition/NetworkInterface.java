package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;

public class NetworkInterface {
    private final Network network; // static final ?

    public NetworkInterface(String networkFileName) {
        network = Importers.loadNetwork(networkFileName, getClass().getResourceAsStream(networkFileName));
        // In the future, we will have to perform checks on this network
    }

    public Network getNetwork() {
        return network;
    }
}
