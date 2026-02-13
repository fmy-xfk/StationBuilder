package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;

public class GuiRectCanvas extends GuiControl{
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    public static class Rect {
        public int width;
        public String text;
        public Rect(int width, String text) {
            this.width = width;
            this.text = text;
        }
    }
    protected int selectedIndex = -1, gap = 4;
    protected ArrayList<Rect> rectangles = new ArrayList<>();

    public GuiRectCanvas(int w, int h) {
        super(0, 0, w, h);
    }

    public int getGap() {
        return gap;
    }
    public void setGap(int gap) {
        this.gap = gap;
    }
    public int getSelectedIndex() {
        return selectedIndex;
    }
    public void select(int index) {
        if (index >= 0 && index < rectangles.size()) {
            this.selectedIndex = index;
        }
    }
    public void clear() {
        rectangles.clear();
        clearSelection();
    }
    public void clearSelection() {
        this.selectedIndex = -1;
    }
    public void addRect(int width, String text) {
        rectangles.add(new Rect(width, text));
    }
    public void removeRect(int index) {rectangles.remove(index);}

    public void swap(int i, int j) {
        Collections.swap(rectangles, i, j);
        if (selectedIndex == i) {
            selectedIndex = j;
        } else if(selectedIndex == j) {
            selectedIndex = i;
        }
    }

    public void removeSelectedRect() {
        if (selectedIndex != -1) {
            rectangles.remove(selectedIndex);
            selectedIndex = -1;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        int cx = x + gap, cy = y + gap, dy = cy + h - 2 * gap;
        context.drawBorder(x, y, w, h, 0xFF888888);
        for(int i = 0; i < rectangles.size(); i++) {
            Rect rect = rectangles.get(i);
            context.fill(cx, cy, cx + rect.width, dy,
                    (i == selectedIndex) ? 0xFFFFFF00 : 0xFF888888);
            int tw = textRenderer.getWidth(rect.text);
            int th = textRenderer.getWrappedLinesHeight(rect.text, tw + 1);;
            context.drawText(
                    textRenderer, rect.text,
                    cx + (rect.width - tw) / 2,
                    cy + (dy - cy - th) / 2,
                    0xFF000000,false
            );
            cx += rect.width + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = x + gap, cy = y + gap, dy = cy + h - 2 * gap;
        // selectedIndex = -1;
        for(int i = 0; i < rectangles.size(); i++) {
            Rect rect = rectangles.get(i);
            if (cx <= mouseX && mouseX <= cx + rect.width &&
                cy <= mouseY && mouseY <= dy) {
                selectedIndex = i;
                break;
            }
            cx += rect.width + gap;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
