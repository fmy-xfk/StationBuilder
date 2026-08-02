package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiLabelSlot extends GuiPanel implements GhostSlotLike {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;

    public class SlotChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public SlotChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlot(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.addControl(this.label).addControl(this.ghostSlot);
        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.slotChanged.invoke(this, new SlotChangedEventArgs(e.newId));
        });
        calcSize();
    }

    public Identifier getBlockId() {
        return ghostSlot.getBlockId(); 
    }

    public void setBlockId(Identifier blockId) {
        this.ghostSlot.setBlockId(blockId);
    }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap();
        h = Math.max(label.h, ghostSlot.h);
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}