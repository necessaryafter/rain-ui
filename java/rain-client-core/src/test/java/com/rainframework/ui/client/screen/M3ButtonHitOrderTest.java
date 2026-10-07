package com.rainframework.ui.client.screen;

import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class M3ButtonHitOrderTest {
    @Test
    void childButtonReceivesClicksAboveItsParent() throws Exception {
        final var contract = parse("""
                {
                  "schemaVersion":0,
                  "id":"test:nested-buttons",
                  "properties":{},
                  "actions":{
                    "test:outer":{"kind":"object","fields":{}},
                    "test:inner":{"kind":"object","fields":{}}
                  },
                  "root":{"type":"box","props":{"width":100,"height":100},"children":[
                    {"type":"button","props":{"position":"absolute","left":10,"top":10,
                      "width":80,"height":80,"action":"test:outer","payload":{}},"children":[
                      {"type":"button","props":{"position":"absolute","left":20,"top":20,
                        "width":30,"height":30,"action":"test:inner","payload":{}},"children":[]}
                    ]}
                  ]}
                }
                """);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        final var interaction = screen.click(90, 90);

        assertNotNull(interaction);
        assertEquals("test:inner", interaction.actionId());
    }

    @Test
    void buttonPaintedAboveInputReceivesTheClick() throws Exception {
        final var contract = parse("""
                {
                  "schemaVersion":0,
                  "id":"test:overlap",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "root":{"type":"stack","props":{"width":100,"height":100},"children":[
                    {"type":"input","props":{"id":"query","value":"",
                      "position":"absolute","left":10,"top":10,"width":80,"height":20},"children":[]},
                    {"type":"button","props":{"action":"test:go","payload":{},"zIndex":1,
                      "position":"absolute","left":10,"top":10,"width":80,"height":20},"children":[]}
                  ]}
                }
                """);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        final var interaction = screen.click(70, 70);

        assertNotNull(interaction);
        assertEquals("test:go", interaction.actionId());
        assertFalse(screen.typeInput("x"));
    }
}
