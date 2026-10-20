package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class GuiLabel extends GuiControl {
    Font textRenderer = Minecraft.getInstance().font;
    protected Component text;
    protected int foreColor = 0xFFFFFF;
    public GuiLabel(int x, int y, Component text) {
        super(x, y, 0, 0);
        setText(text);
    }
    public GuiLabel(Component text) {
        super(0, 0, 0, 0);
        setText(text);
    }
    public int getForeColor() { return foreColor; }
    public void setForeColor(int color) { foreColor = color; }
    public void setText(Component text) {
        this.text = text;
        w = textRenderer.width(text);
        h = textRenderer.wordWrapHeight(text, w + 1);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            context.drawString(textRenderer, text, x, y, foreColor, false);
        }
    }
}
