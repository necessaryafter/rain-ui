package com.rainframework.ui.client.screen;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.draw.Painter;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.packet.Interact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static com.rainframework.ui.client.TestContracts.MONOSPACE;
import static com.rainframework.ui.client.TestContracts.fixture;
import static com.rainframework.ui.client.TestContracts.properties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenControllerTest {
    private final AtomicLong clock = new AtomicLong(1_000);
    private Contract contract;
    private ScreenController controller;

    @BeforeEach
    void openShop() throws Exception {
        contract = fixture("payload-optional");
        controller = new ScreenController(7, "test:payload-optional", contract, 3, properties(contract, """
                { "id": "a1", "note": "urgente" }
                """), clock::get);
        controller.layout(MONOSPACE, 400, 300);
    }

    @Test
    void sendsTheButtonsActionPayloadAndCurrentRevision() {
        final var interact = clickFirstButton();

        assertEquals(new Interact(7, 3, "test:act", "{\"id\":\"a1\"}"), interact);
    }

    @Test
    void ignoresClicksWhilePendingSoADoubleClickSendsOnce() {
        assertNotNull(clickFirstButton());
        assertTrue(controller.isPending());
        assertNull(clickFirstButton());
    }

    @Test
    void leavesThePendingStateOnUpdate() throws Exception {
        clickFirstButton();

        controller.update(4, properties(contract, "{ \"id\": \"a2\" }"));
        controller.layout(MONOSPACE, 400, 300);

        assertFalse(controller.isPending());
        assertEquals(new Interact(7, 4, "test:act", "{\"id\":\"a2\"}"), clickFirstButton());
    }

    @Test
    void leavesThePendingStateOnRejection() {
        clickFirstButton();

        controller.onRejected();

        assertFalse(controller.isPending());
    }

    @Test
    void leavesThePendingStateAfterTheTimeout() {
        clickFirstButton();

        clock.addAndGet(ScreenController.PENDING_TIMEOUT_NANOS - 1);
        assertTrue(controller.isPending());

        clock.addAndGet(1);
        assertFalse(controller.isPending());
    }

    @Test
    void ignoresClicksOutsideButtons() {
        assertNull(controller.click(0, 0));
    }

    @Test
    void ignoresDisabledButtons() throws Exception {
        final var disabled = fixture("optional-bindings");
        final var screen = new ScreenController(1, "test:optional-bindings", disabled, 0,
                properties(disabled, "{ \"locked\": true }"), clock::get);
        screen.layout(MONOSPACE, 400, 300);

        final var button = firstButtonRect(screen);

        assertNull(screen.click(button.x() + 1, button.y() + 1));
    }

    @Test
    void paintsHoverAndPendingButtonColors() {
        final var button = firstButtonRect(controller);

        assertTrue(controller.draw(button.x() + 1, button.y() + 1).contains(
                new DrawCommand.FillRect(button.x(), button.y(), button.width(), button.height(),
                        Painter.BUTTON_HOVER_COLOR)));

        clickFirstButton();

        assertTrue(controller.draw(0, 0).contains(
                new DrawCommand.FillRect(button.x(), button.y(), button.width(), button.height(),
                        Painter.BUTTON_PENDING_COLOR)));
    }

    private Interact clickFirstButton() {
        final var button = firstButtonRect(controller);
        return controller.click(button.x() + 1, button.y() + 1);
    }

    private static DrawCommand.FillRect firstButtonRect(ScreenController screen) {
        return screen.draw(-1, -1).stream()
                .filter(DrawCommand.FillRect.class::isInstance)
                .map(DrawCommand.FillRect.class::cast)
                .filter(rect -> rect.color() != Painter.PANEL_COLOR)
                .findFirst()
                .orElseThrow();
    }
}
