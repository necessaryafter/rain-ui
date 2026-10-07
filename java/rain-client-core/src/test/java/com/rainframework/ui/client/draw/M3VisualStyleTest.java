package com.rainframework.ui.client.draw;

import com.rainframework.ui.client.expand.Expander;
import com.rainframework.ui.client.layout.LayoutEngine;
import org.junit.jupiter.api.Test;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.parse;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3VisualStyleTest {
    private static final String IMAGE_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void visualStylesReachGridScrollButtonAndImagePainting() throws Exception {
        final var contract = parse("""
                {
                  "schemaVersion":0,
                  "id":"test:visual-style",
                  "properties":{},
                  "actions":{"test:go":{"kind":"object","fields":{}}},
                  "assets":{"%s":{"type":"image/png","bytes":1,"width":20,"height":20}},
                  "root":{"type":"box","props":{"width":100,"height":100},"children":[
                    {"type":"grid","props":{"cellWidth":20,"cellHeight":20,"columns":1,
                      "width":50,"height":50,"padding":2,"background":"#123","opacity":0.5,
                      "overflow":"hidden","borderWidth":1,"borderColor":"#fff"},"children":[
                      {"type":"button","props":{"action":"test:go","payload":{},
                        "background":"#f00","opacity":0.5,"overflow":"hidden"},"children":[]}
                    ]},
                    {"type":"scroll","props":{"width":40,"height":40,"background":"#00f",
                      "opacity":0.5},"children":[
                      {"type":"image","props":{"src":{"$asset":"%s"},"width":20,"height":20,
                        "opacity":0.5,"borderWidth":1,"borderColor":"#fff"},"children":[]}
                    ]}
                  ]}
                }
                """.formatted(IMAGE_HASH, IMAGE_HASH));
        final var tree = new Expander(contract).expand(properties(contract, "{}"));
        final var laidOut = new LayoutEngine(MONOSPACE).layout(tree, 200, 200);
        final var commands = new Painter().paint(laidOut, -1, -1, false);
        final var grid = laidOut.children().get(0);
        final var scroll = laidOut.children().get(1);
        final var image = commands.stream()
                .filter(DrawCommand.DrawImage.class::isInstance)
                .map(DrawCommand.DrawImage.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(commands.contains(new DrawCommand.FillRect(grid.x(), grid.y(), 50, 50, 0x80112233)));
        assertTrue(commands.contains(new DrawCommand.PushClip(
                grid.x() + 2, grid.y() + 2, 46, 46)));
        assertTrue(commands.contains(new DrawCommand.PushClip(
                scroll.x(), scroll.y(), scroll.width(), scroll.height())));
        assertEquals(3, commands.stream().filter(DrawCommand.PushClip.class::isInstance).count());
        assertTrue(commands.stream().anyMatch(command -> command instanceof DrawCommand.FillRect fill
                && fill.color() == 0x40FF0000));
        assertEquals(0.25F, image.opacity());
        assertTrue(commands.stream().anyMatch(command -> command instanceof DrawCommand.FillRect fill
                && fill.color() == 0x40FFFFFF));
        assertTrue(commands.stream().anyMatch(command -> command instanceof DrawCommand.FillRect fill
                && fill.color() == 0x800000FF));
    }
}
