package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class GuiLabelTextField extends GuiPanel {
    protected Font textRenderer = Minecraft.getInstance().font;
    protected GuiLabel label;
    protected GuiTextField textField;

    public GuiLabel getLabel() { return label; }
    public GuiTextField getTextField() { return textField; }
    public String getText() {
        return textField.getText();
    }
    public void setText(String text) {
        textField.setText(text);
    }

    public GuiLabelTextField(Component label, int textFieldWidth, int textFieldHeight, Component field) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, field);
        this.addControl(this.label).addControl(this.textField);
        calcSize();
    }

    protected void calcSize() {
        w = label.w + getGap() + textField.getWidth();
        h = Math.max(label.h, textField.getHeight());
    }
    public void setLabelText(Component text) {
        label.setText(text);
        calcSize();
    }
}
