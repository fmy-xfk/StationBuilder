package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class GuiScreen extends Screen {
    protected GuiPanel rootPanel;
    public GuiScreen(Text title) {
        super(title);
        rootPanel = new GuiPanel(width, height);
    }
    public void addControl(GuiControlLike control) { rootPanel.addControl(control); }

    @Override
    protected void init() {
        this.children().clear();
        rootPanel.controls.clear();
        rootPanel.setMargin(0);
        rootPanel.setPadding(10);
        rootPanel.x = 0;
        rootPanel.y = 0;
        rootPanel.w = width;
        rootPanel.h = height;
        rootPanel.setDirection(GuiPanel.PanelDirection.VERTICAL);
        initControls();
        this.addDrawableChild(rootPanel);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        rootPanel.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (rootPanel.mouseClicked(mouseX, mouseY, button)) return true;
        rootPanel.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (rootPanel.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rootPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (rootPanel.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rootPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    public void setDirection(GuiPanel.PanelDirection direction) {
        rootPanel.setDirection(direction);
    }
    public void setMajorAlign(GuiPanel.MajorAlignMode alignment) {
        rootPanel.setMajorAlign(alignment);
    }
    public void setCrossAlign(GuiPanel.CrossAlignMode alignment) {
        rootPanel.setCrossAlign(alignment);
    }
    protected abstract void initControls();

    @Override
    public boolean shouldPause() { return false; }
}