package com.rainframework.ui.fabric.client;

import com.rainframework.ui.client.draw.DrawCommand;
import com.rainframework.ui.client.layout.Measurer;
import com.rainframework.ui.client.screen.ScreenController;
import com.rainframework.ui.client.session.ClientSession;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Draws a Rain screen from the client core's draw commands and forwards clicks and closing to the session. */
final class RainScreen extends Screen {
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

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

    private void draw(GuiGraphicsExtractor graphics, DrawCommand command, long now) {
        switch (command) {
            case DrawCommand.FillRect rect -> graphics.fill(
                    rect.x(),
                    rect.y(),
                    rect.x() + rect.width(),
                    rect.y() + rect.height(),
                    rect.color());
            case DrawCommand.DrawText text -> {
                warnAboutCustomFont(text.fontHash());
                graphics.text(font, text.text(), text.x(), text.y(), text.color(), text.shadow());
            }
            case DrawCommand.DrawItem item -> drawItem(graphics, item);
            case DrawCommand.DrawImage image -> {
                final var texture = textures.texture(image.hash(), now);
                if (texture != null) {
                    // Texture size = drawn size makes the UVs span the whole texture, scaled into the box.
                    final var width = image.width();
                    final var height = image.height();
                    final var tint = (Math.round(image.opacity() * 255) << 24) | 0x00FFFFFF;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, texture, image.x(), image.y(), 0, 0,
                            width, height, width, height, width, height, tint);
                }
            }
            case DrawCommand.PushTransform pushed -> {
                final var transform = pushed.transform();
                final var matrix = new Matrix3x2f(
                        (float) transform.a(), (float) transform.b(),
                        (float) transform.c(), (float) transform.d(),
                        (float) transform.tx(), (float) transform.ty());
                graphics.pose().pushMatrix();
                graphics.pose().mul(matrix);
            }
            case DrawCommand.PopTransform ignored -> graphics.pose().popMatrix();
            case DrawCommand.PushClip clip -> {
                final var pose = graphics.pose();
                pose.pushMatrix();
                pose.identity();

                try {
                    // This version transforms scissor rectangles by the current pose; core supplies screen coordinates.
                    graphics.enableScissor(
                            clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());
                } finally {
                    pose.popMatrix();
                }
            }
            case DrawCommand.PopClip ignored -> graphics.disableScissor();
        }
    }

    // Items render at 16 px, so other sizes scale the pose around the item's corner.
    private void drawItem(GuiGraphicsExtractor graphics, DrawCommand.DrawItem item) {
        final var stack = items.computeIfAbsent(item.itemData(), RainScreen::decodeItem);
        if (stack.isEmpty()) {
            return;
        }

        final var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(item.x(), item.y());
        pose.scale(item.size() / 16f, item.size() / 16f);
        graphics.item(stack, 0, 0);
        graphics.itemDecorations(font, stack, 0, 0);
        pose.popMatrix();
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            session.click(event.x(), event.y());
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
        final var window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return controller.typeInput(new String(Character.toChars(event.codepoint()))) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB
                && controller.focusNextInput((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0)) {
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ENTER && controller.newlineInput()) {
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && controller.backspaceInput()) {
            return true;
        }

        return super.keyPressed(event);
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
