package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import org.jetbrains.annotations.Nullable;

public abstract class GuiControl implements GuiControlLike {
    public GuiControl(int x, int y, int w, int h) {
        this.x = x; this.y = y;
        this.w = w; this.h = h;
    }

    protected GuiControlLike parent = null;
    @Override public @Nullable GuiControlLike getParent() {return parent;}
    @Override public void setParent(GuiControlLike parent) {this.parent = parent;}

    protected int x, y, w, h;
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    protected boolean focused = false, visible = true;
    @Override public void setFocused(boolean focused) {
        this.focused = focused;
    }
    @Override public boolean isFocused() {
        return focused;
    }
    public void setVisible(boolean visible) { this.visible = visible; }

    @Override public boolean isVisible() {
        boolean this_visible = visible;
        GuiControlLike p = parent;
        while(this_visible && p != null) {
            this_visible = p.isVisible();
            p = p.getParent();
        }
        return this_visible;
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return x <= mouseX && mouseX <= x + w && y <= mouseY && mouseY <= y + h;
    }
    @Override public SelectionType getType() {
        return SelectionType.NONE;
    }
    @Override public void appendNarrations(NarrationMessageBuilder builder) {}
}
