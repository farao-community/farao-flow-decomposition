package com.farao_community.farao.flow_decomposition;

import com.powsybl.iidm.network.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class NetworkIndexes {
    List<Branch> xnecList;
    List<Injection<?>> nodeList;
    List<String> nodeIdList;
    List<String> pstList;
    Map<String, Integer> xnecIndex;
    Map<String, Integer> nodeIndex;
    Map<String, Integer> pstIndex;
    NetworkIndexes(Network network) {
        xnecList = selectXnecs(network);
        nodeList = getNodeList(network);
        nodeIdList = getNodeIdList(nodeList);
        pstList = getPstIdList(network);
        xnecIndex = getXnecIndex(xnecList);
        nodeIndex = NetworkUtil.getIndex(nodeIdList);
        pstIndex = NetworkUtil.getIndex(pstList);
    }

    List<Branch> getXnecList() {
        return xnecList;
    }

    List<Injection<?>> getNodeList() {
        return nodeList;
    }

    List<String> getNodeIdList() {
        return nodeIdList;
    }

    List<String> getPstList() {
        return pstList;
    }

    Map<String, Integer> getXnecIndex() {
        return xnecIndex;
    }

    Map<String, Integer> getNodeIndex() {
        return nodeIndex;
    }

    Map<String, Integer> getPstIndex() {
        return pstIndex;
    }

    private List<Branch> selectXnecs(Network network) {
        return network.getBranchStream()
            .filter(this::isAnInterconnection)
            .collect(Collectors.toList());
    }

    private boolean isAnInterconnection(Branch<?> branch) {
        Country country1 = NetworkUtil.getTerminalCountry(branch.getTerminal1());
        Country country2 = NetworkUtil.getTerminalCountry(branch.getTerminal2());
        return !country1.equals(country2);
    }

    private List<Injection<?>> getNodeList(Network network) {
        return getAllNetworkInjections(network)
            .filter(this::isInjectionConnected)
            .filter(this::isInjectionInMainSynchronousComponent)
            .collect(Collectors.toList());
    }

    private Stream<Injection<?>> getAllNetworkInjections(Network network) {
        return network.getConnectableStream()
            .filter(Injection.class::isInstance)
            .map(connectable -> (Injection<?>) connectable);
    }

    private boolean isInjectionConnected(Injection<?> injection) {
        return injection.getTerminal().isConnected();
    }

    private boolean isInjectionInMainSynchronousComponent(Injection<?> injection) {
        return injection.getTerminal().getBusBreakerView().getBus().isInMainSynchronousComponent();
    }

    private List<String> getNodeIdList(List<Injection<?>> nodeList) {
        return nodeList.stream()
            .map(Injection::getId)
            .collect(Collectors.toList());
    }

    private List<String> getPstIdList(Network network) {
        return network.getTwoWindingsTransformerStream()
            .filter(this::hasNeutralStep)
            .map(Identifiable::getId)
            .collect(Collectors.toList());
    }

    private boolean hasNeutralStep(TwoWindingsTransformer pst) {
        PhaseTapChanger phaseTapChanger = pst.getPhaseTapChanger();
        if (phaseTapChanger == null) {
            return false;
        }
        return phaseTapChanger.getNeutralStep().isPresent();
    }

    private Map<String, Integer> getXnecIndex(List<Branch> xnecList) {
        return IntStream.range(0, xnecList.size())
            .boxed()
            .collect(Collectors.toMap(
                i -> xnecList.get(i).getId(),
                Function.identity()
            ));
    }

    int getNumberOfPst() {
        return xnecList.size();
    }
}
