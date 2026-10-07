package com.rainframework.ui.client.screen;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3ClipInteractionTest {
    @Test
    void absoluteButtonOutsideAnUnclippedParentCanBeClickedAndHovered() throws Exception {
        final var screen = open("", "button", "\"action\":\"test:go\",\"payload\":{}");

        assertTrue(screen.draw(85, 55).stream()
                .filter(DrawCommand.FillRect.class::isInstance)
                .map(DrawCommand.FillRect.class::cast)
                .anyMatch(fill -> fill.x() == 80 && fill.y() == 50
                        && fill.color() == Painter.BUTTON_HOVER_COLOR));
        assertNotNull(screen.click(85, 55));
    }

    @Test
    void clippedParentRejectsTheSameButtonAndDoesNotShowHover() throws Exception {
        final var screen = open("\"overflow\":\"hidden\",", "button",
                "\"action\":\"test:go\",\"payload\":{}");

        assertNull(screen.click(85, 55));
        assertTrue(screen.draw(85, 55).stream()
                .filter(DrawCommand.FillRect.class::isInstance)
                .map(DrawCommand.FillRect.class::cast)
                .anyMatch(fill -> fill.x() == 80 && fill.y() == 50
                        && fill.color() == Painter.BUTTON_COLOR));
    }

    @Test
    void inputFocusUsesOnlyActualClipAncestors() throws Exception {
        final var open = open("", "input", "\"id\":\"query\",\"value\":\"\"");

        assertTrue(open.focusNextInput(false));
        assertTrue(open.typeInput("x"));
        assertEquals("x", open.inputValue("query"));

        final var clipped = open("\"overflow\":\"hidden\",", "input",
                "\"id\":\"query\",\"value\":\"\"");

        assertFalse(clipped.focusNextInput(false));
        clipped.click(85, 55);
        assertFalse(clipped.typeInput("x"));
    }

    @Test
    void gridOverflowClipsAbsoluteButtonHitTesting() throws Exception {
        assertNotNull(openGrid(false).click(85, 55));
        assertNull(openGrid(true).click(85, 55));
    }

    private static ScreenController open(String overflow, String type, String props) throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:clip",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "root":{"type":"box","props":{"width":100,"height":100},"children":[
                    {"type":"box","props":{%s"width":20,"height":20},"children":[
                      {"type":"%s","props":{"position":"absolute","left":30,"top":0,
                        "width":20,"height":20,%s},"children":[]}
                    ]}
                  ]}
                }
                """.formatted(overflow, type, props);
        final var contract = parse(json);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        return screen;
    }

    private static ScreenController openGrid(boolean clipped) throws Exception {
        final var overflow = clipped ? "\"overflow\":\"hidden\"," : "";
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:grid-clip",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "root":{"type":"box","props":{"width":100,"height":100},"children":[
                    {"type":"grid","props":{%s"cellWidth":20,"cellHeight":20,
                      "columns":1,"width":20,"height":20},"children":[
                      {"type":"button","props":{"position":"absolute","left":30,"top":0,
                        "width":20,"height":20,"action":"test:go","payload":{}},"children":[]}
                    ]}
                  ]}
                }
                """.formatted(overflow);
        final var contract = parse(json);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        return screen;
    }
}
