package com.rainframework.ui.fabric.client;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.layout.Measurer;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.client.session.ClientSession;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Draws a Rain screen from the client core's draw commands and forwards clicks and closing to the session. */
final class RainScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("rain-ui");

    private final ScreenController controller;
    private final ClientSession session;
    private final AssetTextures textures;
    private final Map<String, ItemStack> items = new HashMap<>();

    private boolean closedByServer = false;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;
    private boolean warnedAboutFonts = false;

    RainScreen(ScreenController controller, ClientSession session, AssetTextures textures) {
        super(Component.literal(controller.getScreenId()));
        this.controller = controller;
        this.session = session;
        this.textures = textures;
    }

    int instanceId() {
        return controller.getInstanceId();
    }

    void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (controller.needsLayout() || width != laidOutWidth || height != laidOutHeight) {
            controller.layout(measurer(), width, height);
            laidOutWidth = width;
            laidOutHeight = height;
        }

        final var now = System.currentTimeMillis();
        for (final var command : controller.draw(mouseX, mouseY)) {
            draw(graphics, command, now);
        }
    }

    private void draw(GuiGraphics graphics, DrawCommand command, long now) {
        switch (command) {
            case DrawCommand.FillRect rect -> graphics.fill(
                    rect.x(),
                    rect.y(),
                    rect.x() + rect.width(),
                    rect.y() + rect.height(),
                    rect.color());
            case DrawCommand.DrawText text -> {
                warnAboutCustomFont(text.fontHash());
                graphics.drawString(font, text.text(), text.x(), text.y(), text.color(), text.shadow());
            }
            case DrawCommand.DrawItem item -> drawItem(graphics, item);
            case DrawCommand.DrawImage image -> {
                final var texture = textures.texture(image.hash(), now);
                if (texture != null) {
                    // Texture size = drawn size makes the UVs span the whole texture, scaled into the box.
                    final var width = image.width();
                    final var height = image.height();
                    graphics.setColor(1.0F, 1.0F, 1.0F, image.opacity());

                    try {
                        graphics.blit(texture, image.x(), image.y(), width, height, 0, 0,
                                width, height, width, height);
                    } finally {
                        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
            }
            case DrawCommand.PushTransform pushed -> {
                final var transform = pushed.transform();
                final var matrix = new Matrix4f()
                        .m00((float) transform.a())
                        .m01((float) transform.b())
                        .m10((float) transform.c())
                        .m11((float) transform.d())
                        .m30((float) transform.tx())
                        .m31((float) transform.ty());
                graphics.pose().pushPose();
                graphics.pose().mulPose(matrix);
            }
            case DrawCommand.PopTransform ignored -> graphics.pose().popPose();
            case DrawCommand.PushClip clip -> graphics.enableScissor(
                    clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());
            case DrawCommand.PopClip ignored -> graphics.disableScissor();
        }
    }

    // Items render at 16 px, so other sizes scale the pose around the item's corner.
    private void drawItem(GuiGraphics graphics, DrawCommand.DrawItem item) {
        final var stack = items.computeIfAbsent(item.itemData(), RainScreen::decodeItem);
        if (stack.isEmpty()) {
            return;
        }

        final var pose = graphics.pose();
        pose.pushPose();
        pose.translate(item.x(), item.y(), 0);
        pose.scale(item.size() / 16f, item.size() / 16f, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(font, stack, 0, 0);
        pose.popPose();
    }

    // The vanilla network codec with this connection's registries, as the server encoded it (decisions.md §8).
    private static ItemStack decodeItem(String base64) {
        final var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return ItemStack.EMPTY;
        }

        final var buffer = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(Base64.getDecoder().decode(base64)),
                connection.registryAccess());
        try {
            return ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        } finally {
            buffer.release();
        }
    }

    // Custom fonts are part of the contract but not rendered yet; text falls back to the default font.
    private void warnAboutCustomFont(@Nullable String fontHash) {
        if (fontHash == null || warnedAboutFonts) {
            return;
        }

        warnedAboutFonts = true;
        RainUIClientLog.warn("Custom fonts are not rendered yet; "
                + controller.getScreenId() + " uses the default font");
    }

    private Measurer measurer() {
        return new Measurer() {

            @Override
            public int textWidth(String text, @Nullable String fontHash) {
                return font.width(text);
            }

            @Override
            public int lineHeight(@Nullable String fontHash) {
                return font.lineHeight;
            }
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            final var sent = session.click(mouseX, mouseY);
            LOGGER.info("Rain UI click on {} at ({}, {}): {}", controller.getScreenId(), mouseX, mouseY,
                    sent ? "sent" : "no enabled button hit");
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (shiftDown() && horizontalAmount == 0) {
            return controller.scroll(mouseX, mouseY, verticalAmount, 0);
        }

        return controller.scroll(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean shiftDown() {
        final var window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return controller.typeInput(character) || super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB
                && controller.focusNextInput((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER && controller.newlineInput()) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && controller.backspaceInput()) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ESC, another screen replacing this one, or a disconnect: the server is told unless it asked for the close.
    @Override
    public void removed() {
        textures.releaseAll();
        if (!closedByServer) {
            session.onClosedByPlayer();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
