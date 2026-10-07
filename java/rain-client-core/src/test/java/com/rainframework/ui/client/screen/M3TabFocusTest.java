package com.rainframework.ui.client.screen;

import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3TabFocusTest {
    @Test
    void hiddenTabKeepsInputTextButCannotKeepKeyboardFocus() throws Exception {
        final var contract = parse("""
                {
                  "schemaVersion":0,
                  "id":"test:tab-focus",
                  "properties":{},
                  "actions":{},
                  "root":{"type":"box","props":{},"children":[
                    {"type":"tabs","props":{"defaultTab":"first"},"children":[
                      {"type":"tab","props":{"id":"first","label":"First"},"children":[
                        {"type":"input","props":{"id":"query","value":""},"children":[]}
                      ]},
                      {"type":"tab","props":{"id":"second","label":"Second"},"children":[
                        {"type":"text","props":{"value":"Other"},"children":[]}
                      ]}
                    ]}
                  ]}
                }
                """);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 100);

        assertTrue(screen.focusNextInput(false));
        assertTrue(screen.typeInput("x"));
        screen.selectTab("first", "second");

        assertFalse(screen.typeInput("y"));
        assertEquals("x", screen.inputValue("query"));

        screen.selectTab("first", "first");
        screen.layout(MONOSPACE, 200, 100);

        assertTrue(screen.focusNextInput(false));
        assertTrue(screen.typeInput("z"));
        assertEquals("xz", screen.inputValue("query"));
    }
}
