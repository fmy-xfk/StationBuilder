package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class GuiLabelButton extends GuiPanel {
    protected Font textRenderer = Minecraft.getInstance().font;
    protected GuiLabel label;
    protected GuiButton button;

    public GuiLabel getLabel() { return label; }
    public GuiButton getButton() { return button; }
    public void setMessage(Component msg) {
        button.setMessage(msg);
    }

    public GuiLabelButton(Component buttonMessage, GuiButton.PressAction onPress, int buttonWidth, int buttonHeight, Component label) {
        super(buttonWidth, buttonHeight);
        this.label = new GuiLabel(label);
        this.button = new GuiButton(buttonMessage, onPress, buttonWidth, buttonHeight);
        this.addControl(this.label).addControl(this.button);
        calcSize();
    }

    protected void calcSize() {
        w = label.w + getGap() + button.getWidth();
        h = Math.max(label.h, button.getHeight());
    }
    public void setLabelText(Component text) {
        label.setText(text);
        calcSize();
    }
}
