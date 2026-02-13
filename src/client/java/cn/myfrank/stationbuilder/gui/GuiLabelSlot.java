package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiLabelSlot extends GuiPanel implements GhostSlotLike {
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

    public GuiLabelSlot(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, Text.of(blockId.toString())); // 确保初始化文本是 blockId 的字符串形式
        this.textField.textChanged.addHandler((sender, e) -> {
            if (Identifier.isValid(e.newText)) {
                this.ghostSlot.setBlockId(new Identifier(e.newText), false);
                this.slotChanged.invoke(this, new SlotChangedEventArgs(new Identifier(e.newText)));
            }
        });
        this.addControl(this.label).addControl(this.ghostSlot).addControl(this.textField);
        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.textField.setText(e.newId.toString(), false);
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
        w = label.w + getGap() + ghostSlot.w + getGap() + textField.getWidth();
        h = Math.max(label.h, Math.max(ghostSlot.h, textField.getHeight()));
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }

    @Override
    public void setFocused(boolean focused) {
        this.ghostSlot.setActive(focused);
        super.setFocused(focused);
    }

    public boolean isActive() { return this.ghostSlot.isActive(); }
    public void setActive(boolean active) { this.ghostSlot.setActive(active); }
}