package com.rainframework.ui.client.screen;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.protocol.packet.Interact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.fixture;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3ScreenControllerTest {
    private ScreenController controller;

    @BeforeEach
    void openScreen() throws Exception {
        final var contract = fixture("m3-composition");
        controller = new ScreenController(
                4,
                contract.id(),
                contract,
                1,
                properties(contract, """
                        {
                          "accent": "#fff",
                          "barWidth": 10,
                          "icon": "fire",
                          "items": [
                            { "id": "one", "name": "One" },
                            { "id": "two", "name": "Two" },
                            { "id": "three", "name": "Three" },
                            { "id": "four", "name": "Four" },
                            { "id": "five", "name": "Five" },
                            { "id": "six", "name": "Six" },
                            { "id": "seven", "name": "Seven" },
                            { "id": "eight", "name": "Eight" },
                            { "id": "nine", "name": "Nine" },
                            { "id": "ten", "name": "Ten" }
                          ]
                        }
                        """),
                System::nanoTime);
        controller.layout(MONOSPACE, 400, 600);
    }

    @Test
    void editsLocalInputAndIncludesItInTheActionPayload() {
        final var placeholder = textCommand("Search");

        controller.click(placeholder.x() - 2, placeholder.y() - 4);
        assertTrue(controller.typeInput("hi"));
        assertEquals("hi", controller.inputValue("query"));
        assertTrue(controller.needsLayout());

        controller.layout(MONOSPACE, 400, 600);
        final var label = textCommand("One");
        final var interaction = controller.click(label.x() - 3, label.y() - 3);

        assertNotNull(interaction);
        assertEquals(new Interact(4, 1, "test:select", "{\"id\":\"one\",\"query\":\"hi\"}"), interaction);
    }

    @Test
    void changesTabsWithoutSendingAnInteraction() {
        final var other = textCommand("Other");

        controller.click(other.x() - 5, other.y() - 5);

        assertTrue(controller.needsLayout());
        controller.layout(MONOSPACE, 400, 600);
        assertEquals(1, controller.draw(-1, -1).stream()
                .filter(command -> command instanceof DrawCommand.DrawText text
                        && text.text().equals("Details"))
                .count());
    }

    @Test
    void scrollsContentWithinItsViewport() {
        final var before = textCommand("One").y();

        assertTrue(controller.scroll(10, 130, 0, -1));
        assertTrue(controller.needsLayout());

        controller.layout(MONOSPACE, 400, 600);
        assertEquals(before - 12, textCommand("One").y());
        assertTrue(controller.draw(-1, -1).stream().anyMatch(DrawCommand.PushClip.class::isInstance));
    }

    @Test
    void clampsScrollOffsetWhenAnUpdateShrinksTheContent() throws Exception {
        final var before = textCommand("One").y();
        assertTrue(controller.scroll(10, 130, 0, -1));
        controller.layout(MONOSPACE, 400, 600);
        assertEquals(before - 12, textCommand("One").y());

        controller.update(2, properties(controller.getContract(), """
                {
                  "accent": "#fff",
                  "barWidth": 10,
                  "icon": "fire",
                  "items": [{ "id": "one", "name": "One" }]
                }
                """));
        controller.layout(MONOSPACE, 400, 600);

        assertEquals(before, textCommand("One").y());
    }

    private DrawCommand.DrawText textCommand(String value) {
        return controller.draw(-1, -1).stream()
                .filter(command -> command instanceof DrawCommand.DrawText text && text.text().equals(value))
                .map(DrawCommand.DrawText.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
