package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GuiLabelSlotInput extends GuiPanel implements GhostSlotLike {
    protected Font textRenderer = Minecraft.getInstance().font;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;
    protected GuiTextField textField;

    public class SlotChangedEventArgs extends EventArgs {
        public final ResourceLocation newId;
        public SlotChangedEventArgs(ResourceLocation newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlotInput(Component label, int textFieldWidth, int textFieldHeight, ResourceLocation blockId, boolean active, Component text) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, text);
        this.addControl(this.label).addControl(this.ghostSlot).addControl(this.textField);
        
        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.slotChanged.invoke(this, new SlotChangedEventArgs(e.newId));
        });
        
        calcSize();
    }

    public GuiLabel getLabel() { return label;}
    public GhostSlot getGhostSlot() { return ghostSlot; }
    public GuiTextField getTextField() { return textField; }
    public String getText() { return textField.getText(); }
    public void setText(String text) { textField.setText(text);} 
    public ResourceLocation getBlockId() { return ghostSlot.getBlockId(); }

    @Override
    public void setBlockId(ResourceLocation blockId) { this.ghostSlot.setBlockId(blockId); }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap() + textField.getWidth();
        h = Math.max(label.h, Math.max(ghostSlot.h, textField.getHeight()));
    }

    public void setLabelText(Component text) {
        label.setText(text);
        calcSize();
    }
}