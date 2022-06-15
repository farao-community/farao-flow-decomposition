package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;

import java.util.*;
import java.util.stream.Collectors;

public class ZoneInterface {

    private static final Country DEFAULTCOUNTRY = null;
    public Set<Country> getZones(Network network) {
        return network.getSubstationStream().map(substation -> substation.getCountry().orElse(DEFAULTCOUNTRY)).collect(Collectors.toSet());
    }
}
