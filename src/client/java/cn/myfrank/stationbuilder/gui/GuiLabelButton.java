package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class GuiLabelButton extends GuiPanel {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GuiButton button;

    public GuiLabel getLabel() { return label; }
    public GuiButton getButton() { return button; }
    public void setMessage(Text msg) {
        button.setMessage(msg);
    }

    public GuiLabelButton(Text buttonMessage, GuiButton.PressAction onPress, int buttonWidth, int buttonHeight, Text label) {
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
    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}
