package com.rainframework.ui.client.screen;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.protocol.Contract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.MAPPER;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3IdentityTest {
    private static final String INITIAL = """
            {"items":[
              {"id":"a","name":"A","query":"alpha"},
              {"id":"b","name":"B","query":"beta"}
            ]}
            """;
    private static final String REORDERED = """
            {"items":[
              {"id":"b","name":"B","query":"beta"},
              {"id":"a","name":"A","query":"alpha"}
            ]}
            """;

    @Test
    void keyedInputAndFocusFollowTheItemAfterReordering() throws Exception {
        final var screen = open(true);
        final var input = textCommand(screen, "alpha");

        screen.click(input.x() - 2, input.y() - 4);
        assertTrue(screen.typeInput("!"));

        screen.update(2, properties(screen.getContract(), REORDERED));
        screen.layout(MONOSPACE, 400, 300);

        assertTrue(screen.typeInput("?"));
        screen.layout(MONOSPACE, 400, 300);
        assertNotNull(textCommand(screen, "alpha!?"));

        final var button = textCommand(screen, "A");
        final var interaction = screen.click(button.x() - 3, button.y() - 3);

        assertNotNull(interaction);
        final var payload = MAPPER.readTree(interaction.payloadJson());
        assertEquals("a", payload.path("id").asText());
        assertEquals("alpha!?", payload.path("query").asText());
    }

    @Test
    void unkeyedInputResetsAfterPropertiesUpdate() throws Exception {
        final var screen = open(false);
        final var input = textCommand(screen, "alpha");

        screen.click(input.x() - 2, input.y() - 4);
        assertTrue(screen.typeInput("!"));

        screen.update(2, properties(screen.getContract(), REORDERED));
        screen.layout(MONOSPACE, 400, 300);

        assertFalse(screen.typeInput("?"));
        assertNotNull(textCommand(screen, "alpha"));
        assertEquals(List.of("beta", "alpha"), inputTexts(screen));
    }

    @Test
    void duplicateResolvedKeysRejectTheUpdate() throws Exception {
        final var screen = open(true);
        final var duplicate = """
                {"items":[
                  {"id":"a","name":"A","query":"alpha"},
                  {"id":"a","name":"B","query":"beta"}
                ]}
                """;

        assertThrows(IllegalArgumentException.class,
                () -> screen.update(2, properties(screen.getContract(), duplicate)));
        assertEquals(1, screen.getRevision());
    }

    @Test
    void payloadReadsAnInputDeclaredAfterItsButtonAtClickTime() throws Exception {
        final var screen = open(true);
        final var button = textCommand(screen, "A");
        final var interaction = screen.click(button.x() - 3, button.y() - 3);

        assertNotNull(interaction);
        assertEquals("alpha", MAPPER.readTree(interaction.payloadJson()).path("query").asText());
    }

    private static ScreenController open(boolean keyed) throws Exception {
        final var contract = contract(keyed);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, INITIAL), System::nanoTime);
        screen.layout(MONOSPACE, 400, 300);

        return screen;
    }

    private static Contract contract(boolean keyed) throws Exception {
        final var key = keyed ? "\"key\":{\"$bind\":\"id\"}," : "";
        final var payloadQuery = keyed ? "{\"$input\":\"query\"}" : "\"fixed\"";
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:identity",
                  "properties":{"items":{"kind":"list","of":{"kind":"object","fields":{
                    "id":{"kind":"string"},"name":{"kind":"string"},"query":{"kind":"string"}
                  }}}},
                  "actions":{"test:select":{"kind":"object","fields":{
                    "id":{"kind":"string"},"query":{"kind":"string"}
                  }}},
                  "root":{"type":"box","props":{},"children":[
                    {"type":"list","props":{"source":{"$bind":"items"}},"children":[
                      {"type":"box",%s"props":{},"children":[
                        {"type":"button","props":{"action":"test:select","payload":{
                          "id":{"$bind":"id"},"query":%s
                        }},"children":[
                          {"type":"text","props":{"value":{"$bind":"name"}},"children":[]}
                        ]},
                        {"type":"input","props":{"id":"query","value":{"$bind":"query"}},"children":[]}
                      ]}
                    ]}
                  ]}
                }
                """.formatted(key, payloadQuery);

        return parse(json);
    }

    private static DrawCommand.DrawText textCommand(ScreenController screen, String value) {
        return screen.draw(-1, -1).stream()
                .filter(command -> command instanceof DrawCommand.DrawText text && text.text().equals(value))
                .map(DrawCommand.DrawText.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> inputTexts(ScreenController screen) {
        return screen.draw(-1, -1).stream()
                .filter(DrawCommand.DrawText.class::isInstance)
                .map(DrawCommand.DrawText.class::cast)
                .map(DrawCommand.DrawText::text)
                .filter(value -> value.equals("alpha") || value.equals("beta"))
                .toList();
    }
}
