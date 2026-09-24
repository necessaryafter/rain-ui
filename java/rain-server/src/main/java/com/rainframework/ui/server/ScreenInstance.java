package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rainframework.ui.protocol.Contract;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** One screen open for one player. Its revision goes up with every update, so late clicks can be told apart. */
@Getter
public final class ScreenInstance {
    private final int id;
    private final PlayerRef player;
    private final String screenId;
    private final String contractHash;
    private final Contract contract;

    @Setter(AccessLevel.PACKAGE)
    private int revision;
    @Setter(AccessLevel.PACKAGE)
    private ObjectNode properties;
    @Setter(AccessLevel.PACKAGE)
    private boolean open = true;

    ScreenInstance(int id, PlayerRef player, ContractRegistry.LoadedScreen screen, ObjectNode properties) {
        this.id = id;
        this.player = player;
        this.screenId = screen.id();
        this.contractHash = screen.hash();
        this.contract = screen.contract();
        this.properties = properties;
    }
}
