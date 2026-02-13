package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiLabelSlotInput extends GuiPanel implements GhostSlotLike {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;
    protected GuiTextField textField;

    public GuiLabel getLabel() { return label;}
    public GhostSlot getGhostSlot() { return ghostSlot; }
    public GuiTextField getTextField() { return textField; }
    public String getText() { return textField.getText(); }
    public void setText(String text) { textField.setText(text);} 
    public boolean isActive() { return this.ghostSlot.isActive(); }
    public void setActive(boolean active) { this.ghostSlot.setActive(active); }
    public Identifier getBlockId() { return ghostSlot.getBlockId(); }
    public void setBlockId(Identifier blockId) { this.ghostSlot.setBlockId(blockId); }

    public GuiLabelSlotInput(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active, Text text) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, text);
        this.addControl(this.label).addControl(this.ghostSlot).addControl(this.textField);
        calcSize();
    }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap() + textField.getWidth();
        h = Math.max(label.h, Math.max(ghostSlot.h, textField.getHeight()));
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}
