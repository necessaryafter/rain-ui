package com.rainframework.ui.client.screen;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3TransformInteractionTest {
    @Test
    void rotatedButtonUsesItsPaintedRegionForHoverAndClick() throws Exception {
        final var screen = open("\"width\":100,\"height\":100", ",\"rotate\":90", 40, 20);

        assertTrue(screen.draw(90, 65).stream().anyMatch(command -> command instanceof DrawCommand.FillRect fill
                && fill.x() == 70 && fill.y() == 70 && fill.color() == Painter.BUTTON_HOVER_COLOR));
        assertTrue(screen.draw(90, 65).stream().anyMatch(DrawCommand.PushTransform.class::isInstance));
        assertNull(screen.click(75, 75));
        assertNotNull(screen.click(90, 65));
    }

    @Test
    void scaledAncestorMovesItsButtonsHitRegion() throws Exception {
        final var screen = open("\"width\":100,\"height\":100,\"scale\":2", "", 40, 20);

        assertNotNull(screen.click(50, 50));
    }

    @Test
    void nestedScaleAndRotationComposeForHitTesting() throws Exception {
        final var screen = open("\"width\":100,\"height\":100,\"scale\":2",
                ",\"rotate\":90", 40, 20);

        assertNotNull(screen.click(80, 30));
    }

    @Test
    void scaledClipUsesTheSameScreenViewportForPaintingAndHitTesting() throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:scaled-clip",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "root":{"type":"box","props":{"width":40,"height":40,
                    "overflow":"hidden","scale":2},"children":[
                    {"type":"button","props":{"position":"absolute","left":30,"top":10,
                      "width":20,"height":20,"action":"test:go","payload":{}},"children":[]}
                  ]}
                }
                """;
        final var contract = parse(json);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        assertTrue(screen.draw(-1, -1).contains(new DrawCommand.PushClip(60, 60, 80, 80)));
        assertNull(screen.click(150, 100));
        assertNotNull(screen.click(130, 100));
    }

    private static ScreenController open(String rootProps, String buttonProps, int width, int height)
            throws Exception {
        final var json = """
                {
                  "schemaVersion":0,
                  "id":"test:transform",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "root":{"type":"box","props":{%s},"children":[
                    {"type":"button","props":{"position":"absolute","left":20,"top":20,
                      "width":%d,"height":%d,"action":"test:go","payload":{}%s},"children":[]}
                  ]}
                }
                """.formatted(rootProps, width, height, buttonProps);
        final var contract = parse(json);
        final var screen = new ScreenController(
                1, contract.id(), contract, 1, properties(contract, "{}"), System::nanoTime);
        screen.layout(MONOSPACE, 200, 200);

        return screen;
    }
}
