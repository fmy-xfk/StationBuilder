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

    public class SlotChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public SlotChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlotInput(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active, Text text) {
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
    public Identifier getBlockId() { return ghostSlot.getBlockId(); }

    @Override
    public void setBlockId(Identifier blockId) { this.ghostSlot.setBlockId(blockId); }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap() + textField.getWidth();
        h = Math.max(label.h, Math.max(ghostSlot.h, textField.getHeight()));
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}