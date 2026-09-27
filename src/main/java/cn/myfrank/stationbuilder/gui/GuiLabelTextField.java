package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class GuiLabelTextField extends GuiPanel {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
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

    public GuiLabelTextField(Text label, int textFieldWidth, int textFieldHeight, Text field) {
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
    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}
