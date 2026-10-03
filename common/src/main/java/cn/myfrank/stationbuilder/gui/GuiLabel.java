package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class GuiLabel extends GuiControl {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected Text text;
    protected int foreColor = 0xFFFFFF;
    public GuiLabel(int x, int y, Text text) {
        super(x, y, 0, 0);
        setText(text);
    }
    public GuiLabel(Text text) {
        super(0, 0, 0, 0);
        setText(text);
    }
    public int getForeColor() { return foreColor; }
    public void setForeColor(int color) { foreColor = color; }
    public void setText(Text text) {
        this.text = text;
        w = textRenderer.getWidth(text);
        h = textRenderer.getWrappedLinesHeight(text, w + 1);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            context.drawText(textRenderer, text, x, y, foreColor, false);
        }
    }
}
