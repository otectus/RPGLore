package com.rpglore.codex;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A red ribbon tab that sticks out of the book's right edge and cycles a value
 * on click. Unlike a CycleButton it spells out role and value in full
 * ("Category: All"), because the ribbon has the width for it.
 */
@OnlyIn(Dist.CLIENT)
public class RibbonButton<T> extends AbstractButton {

    private static final ResourceLocation TEX =
            new ResourceLocation("rpg_lore", "textures/gui/codex.png");

    public static final int HEIGHT = 12;
    /** Pixels of the ribbon root hidden under the book border. */
    public static final int TUCK = 3;

    // Ribbon sprite: left cap, one-pixel stretched middle, swallowtail end
    private static final int CAP_U = 170;
    private static final int CAP_W = 4;
    private static final int MID_U = 174;
    private static final int TAIL_U = 175;
    private static final int TAIL_W = 6;
    private static final int V_NORMAL = 1;
    private static final int V_HOVER = 14;

    /** Inner padding between the cap/tail and the label. */
    private static final int PAD = 3;

    private static final int COLOR_TEXT = 0xF1E2B8;

    private final Font font;
    private final List<T> values;
    private final String formatKey;
    private final Function<T, Component> valueLabel;
    private final BiConsumer<RibbonButton<T>, T> onChange;
    private final int textWidth;

    private int index;

    public RibbonButton(Font font, int x, int y, int maxTextWidth, List<T> values, T initial,
                        String formatKey, Function<T, Component> valueLabel,
                        BiConsumer<RibbonButton<T>, T> onChange) {
        super(x, y, widthFor(font, values, maxTextWidth, formatKey, valueLabel), HEIGHT,
                Component.translatable(formatKey, valueLabel.apply(initial)));
        this.font = font;
        this.values = values;
        this.formatKey = formatKey;
        this.valueLabel = valueLabel;
        this.onChange = onChange;
        this.textWidth = textWidthFor(font, values, maxTextWidth, formatKey, valueLabel);

        int initialIndex = values.indexOf(initial);
        this.index = initialIndex < 0 ? 0 : initialIndex;
        setTooltip(Tooltip.create(labelOf(getValue())));
    }

    private static <T> int textWidthFor(Font font, List<T> values, int maxTextWidth,
                                        String formatKey, Function<T, Component> valueLabel) {
        int widest = 0;
        for (T value : values) {
            widest = Math.max(widest, font.width(Component.translatable(formatKey, valueLabel.apply(value))));
        }
        return Math.min(widest, maxTextWidth);
    }

    private static <T> int widthFor(Font font, List<T> values, int maxTextWidth,
                                    String formatKey, Function<T, Component> valueLabel) {
        return CAP_W + PAD + textWidthFor(font, values, maxTextWidth, formatKey, valueLabel) + PAD + TAIL_W;
    }

    private Component labelOf(T value) {
        return Component.translatable(formatKey, valueLabel.apply(value));
    }

    public T getValue() {
        return values.get(index);
    }

    @Override
    public void onPress() {
        // Shift walks the cycle backwards, as the vanilla cycle buttons do
        int step = Screen.hasShiftDown() ? -1 : 1;
        index = Math.floorMod(index + step, values.size());
        T value = getValue();
        Component label = labelOf(value);
        setMessage(label);
        setTooltip(Tooltip.create(label));
        onChange.accept(this, value);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int v = isHoveredOrFocused() ? V_HOVER : V_NORMAL;

        graphics.blit(TEX, getX(), getY(), CAP_U, v, CAP_W, HEIGHT);
        // One texture pixel stretched across the ribbon body
        graphics.blit(TEX, getX() + CAP_W, getY(), width - CAP_W - TAIL_W, HEIGHT,
                (float) MID_U, (float) v, 1, HEIGHT, 256, 256);
        graphics.blit(TEX, getX() + width - TAIL_W, getY(), TAIL_U, v, TAIL_W, HEIGHT);

        FormattedCharSequence line = font.split(getMessage(), textWidth).stream()
                .findFirst().orElse(FormattedCharSequence.EMPTY);
        graphics.drawString(font, line, getX() + CAP_W + PAD, getY() + 2, COLOR_TEXT, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
