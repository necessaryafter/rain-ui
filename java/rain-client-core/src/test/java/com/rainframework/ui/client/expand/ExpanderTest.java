package com.rainframework.ui.client.expand;

import com.rainframework.ui.client.render.RenderNode;
import com.rainframework.ui.protocol.Contract;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.rainframework.ui.client.TestContracts.fixture;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpanderTest {

    @Test
    void resolvesBindingsThroughNestedObjectsAndLists() throws Exception {
        final var contract = fixture("nested-binding");
        final var tree = expand(contract, """
                {
                  "owner": { "name": "Ash" },
                  "listings": [
                    { "name": "Pikachu", "bidData": { "bidderName": "Misty", "bids": [{ "bidderName": "Brock" }] } }
                  ]
                }
                """);

        assertEquals(List.of("Ash", "Pikachu", "Misty", "Brock"), texts(tree));
    }

    @Test
    void expandsAListInsideARowIntoThatRow() throws Exception {
        final var contract = fixture("nested-list");
        final var tree = expand(contract, """
                { "rows": [ { "cells": [ { "label": "a" }, { "label": "b" } ] }, { "cells": [ { "label": "c" } ] } ] }
                """);

        assertEquals(List.of("a", "b", "c"), texts(tree));
    }

    @Test
    void appliesDefaultsWhenValuesAreAbsent() throws Exception {
        final var contract = fixture("defaults");
        final var tree = expand(contract, """
                { "listings": [ {}, { "label": "x" } ] }
                """);

        assertEquals(List.of("Sem descrição", "-", "x"), texts(tree));
    }

    @Test
    void usesEachPropsOwnDefaultForAbsentOptionalBindings() throws Exception {
        final var contract = fixture("optional-bindings");
        final var tree = expand(contract, "{}");

        final var text = (RenderNode.Text) tree.children().get(0);
        assertEquals("", text.value());
        assertEquals(Expander.DEFAULT_TEXT_COLOR, text.color());

        final var button = (RenderNode.Button) tree.children().get(1);
        assertFalse(button.disabled());
        assertEquals(2, tree.children().size(), "an absent item and an absent list draw nothing");
    }

    @Test
    void showsNumbersAsRawDecimals() throws Exception {
        final var contract = fixture("text-number");

        assertEquals(List.of("1250", "9000000000"), texts(expand(contract, """
                { "count": 1250, "total": 9000000000 }
                """)));
    }

    @Test
    void parsesColorsAndFallsBackOnABadRuntimeValue() throws Exception {
        final var contract = fixture("color");

        final var good = expand(contract, "{ \"tint\": \"#FF0000\" }");
        assertEquals(0xFF4CAF50, ((RenderNode.Text) good.children().get(0)).color());
        assertEquals(0xFFFF0000, ((RenderNode.Text) good.children().get(1)).color());

        final var bad = expand(contract, "{ \"tint\": \"red\" }");
        assertEquals(Expander.DEFAULT_TEXT_COLOR, ((RenderNode.Text) bad.children().get(1)).color());
    }

    @Test
    void showsContentOrFallbackByTheEmptinessRules() throws Exception {
        final var contract = fixture("show");

        final var present = expand(contract, """
                { "locked": true, "subtitle": "Olá", "listings": [{ "name": "a" }], "owner": { "name": "Ash" } }
                """);
        assertEquals(List.of("Locked", "Olá", "a", "Ash"), texts(present));

        final var empty = expand(contract, """
                { "locked": false, "subtitle": "", "listings": [], "owner": { "name": "Ash" } }
                """);
        assertEquals(List.of("Sem descrição", "Vazio", "Ash"), texts(empty));
    }

    @Test
    void matchesCasesByValueAndFallsBackToDefault() throws Exception {
        final var contract = fixture("match");

        assertEquals(List.of("Vendido", "Dois", "Não", "Falha na entrega"), texts(expand(contract, """
                { "status": "SOLD", "level": 2, "active": false, "reason": "DELIVERY_FAILED" }
                """)));
        assertEquals(List.of("Outro", "Sim", "Sem pendência"), texts(expand(contract, """
                { "status": "EXPIRED", "level": 3, "active": true }
                """)));
    }

    @Test
    void resolvesButtonPayloadsPerListItem() throws Exception {
        final var contract = fixture("double");
        final var tree = expand(contract, """
                { "price": 1, "listings": [ { "id": "a1", "price": 12.5 }, { "id": "a2", "price": 3 } ] }
                """);

        final var buttons = buttons(tree);
        assertEquals("{\"amount\":12.5,\"listingId\":\"a\"}", buttons.get(0).payloadJson());
        assertEquals("{\"amount\":12.5,\"listingId\":\"a1\"}", buttons.get(1).payloadJson());
        assertEquals("{\"amount\":3,\"listingId\":\"a2\"}", buttons.get(2).payloadJson());
    }

    @Test
    void leavesAbsentOptionalValuesOutOfThePayload() throws Exception {
        final var contract = fixture("payload-optional");
        final var tree = expand(contract, "{ \"id\": \"x\" }");

        assertEquals("{\"id\":\"x\"}", buttons(tree).get(1).payloadJson());
    }

    @Test
    void resolvesServerChosenAssetNamesToHashes() throws Exception {
        final var contract = fixture("assets-named");
        final var tree = expand(contract, "{ \"icon\": \"water\" }");

        final var image = (RenderNode.Image) tree.children().getFirst();
        assertEquals(contract.assetNames().get("water"), image.hash());
        assertEquals(1, tree.children().size(), "an absent optional asset draws nothing");
    }

    @Test
    void marksDisabledButtons() throws Exception {
        final var contract = fixture("optional-bindings");

        assertTrue(buttons(expand(contract, "{ \"locked\": true }")).getFirst().disabled());
    }

    private static RenderNode.Box expand(Contract contract, String json) throws Exception {
        return new Expander(contract).expand(properties(contract, json));
    }

    static List<String> texts(RenderNode node) {
        final var texts = new ArrayList<String>();
        collect(node, texts, new ArrayList<>());
        return texts;
    }

    static List<RenderNode.Button> buttons(RenderNode node) {
        final var buttons = new ArrayList<RenderNode.Button>();
        collect(node, new ArrayList<>(), buttons);
        return buttons;
    }

    private static void collect(RenderNode node, List<String> texts, List<RenderNode.Button> buttons) {
        switch (node) {
            case RenderNode.Text text -> texts.add(text.value());
            case RenderNode.Button button -> {
                buttons.add(button);
                collect(button.content(), texts, buttons);
            }
            case RenderNode.Box box -> box.children().forEach(child -> collect(child, texts, buttons));
            default -> {
            }
        }
    }
}
