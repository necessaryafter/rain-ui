package com.rainframework.ui.client.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import com.rainframework.ui.client.expand.Expander;
import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.protocol.ContractParser;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3FlowExpansionTest {
    private final LayoutEngine engine = new LayoutEngine(MONOSPACE);

    @Test
    void usesPerSideSpacingAndDirectParentAnchorsFromAContract() throws Exception {
        final var root = expand("""
                {"type":"box","props":{"width":100,"height":50,
                  "padding":{"top":2,"right":3,"bottom":4,"left":5}},"children":[
                  {"type":"box","props":{"position":"absolute","right":6,"bottom":7,
                    "width":20,"height":10,"margin":{"top":1,"right":4,"bottom":2,"left":3}},"children":[]},
                  {"type":"box","props":{"width":10,"height":10,"left":4,"top":3},"children":[]}
                ]}
                """);

        final var laidOut = engine.layout(root, 200, 100);

        assertEquals(laidOut.x() + 5 + 4, laidOut.children().get(1).x());
        assertEquals(laidOut.y() + 2 + 3, laidOut.children().get(1).y());
        assertEquals(laidOut.x() + 100 - 3 - 4 - 6 - 20, laidOut.children().get(0).x());
        assertEquals(laidOut.y() + 50 - 4 - 2 - 7 - 10, laidOut.children().get(0).y());
    }

    @Test
    void appliesDeclaredGrowWeights() throws Exception {
        final var root = expand("""
                {"type":"row","props":{"width":100,"height":20},"children":[
                  {"type":"box","props":{"width":10,"height":10,"grow":1},"children":[]},
                  {"type":"box","props":{"width":10,"height":10,"grow":3},"children":[]}
                ]}
                """);

        final var laidOut = engine.layout(root, 200, 100);

        assertEquals(30, laidOut.children().get(0).width());
        assertEquals(70, laidOut.children().get(1).width());
    }

    @Test
    void onlyLegacyRootsGetTheImplicitPanel() throws Exception {
        final var painter = new Painter();
        final var box = expand("""
                {"type":"box","props":{"width":20,"height":10},"children":[]}
                """);
        final var column = expand("""
                {"type":"column","props":{"width":20,"height":10},"children":[]}
                """);

        assertTrue(painter.paint(engine.layout(box, 100, 100), -1, -1, false).isEmpty());
        assertEquals(new DrawCommand.FillRect(40, 45, 20, 10, Painter.PANEL_COLOR),
                painter.paint(engine.layout(column, 100, 100), -1, -1, false).getFirst());
    }

    private static RenderNode.Box expand(String root) throws Exception {
        final var json = """
                {"schemaVersion":0,"id":"test:flow","properties":{},"actions":{},"root":%s}
                """.formatted(root);
        final var contract = new ContractParser().parse(json);

        return new Expander(contract).expand(new ObjectMapper().readTree("{}"));
    }
}
