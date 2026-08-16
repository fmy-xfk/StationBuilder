package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class GuiLabelSlot extends GuiPanel implements GhostSlotLike {
    protected Font textRenderer = Minecraft.getInstance().font;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;

    public class SlotChangedEventArgs extends EventArgs {
        public final ResourceLocation newId;
        public SlotChangedEventArgs(ResourceLocation newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlot(Component label, int textFieldWidth, int textFieldHeight, ResourceLocation blockId, boolean active) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.addControl(this.label).addControl(this.ghostSlot);
        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.slotChanged.invoke(this, new SlotChangedEventArgs(e.newId));
        });
        calcSize();
    }

    public ResourceLocation getBlockId() {
        return ghostSlot.getBlockId(); 
    }

    @Override
    public void setBlockId(ResourceLocation blockId) {
        this.ghostSlot.setBlockId(blockId);
    }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap();
        h = Math.max(label.h, ghostSlot.h);
    }

    public void setLabelText(Component text) {
        label.setText(text);
        calcSize();
    }
}