package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class GhostSlot extends GuiControl implements GhostSlotLike {
    protected static final int SLOT_SIZE = 18;
    private Identifier blockId;
    private boolean active;

    public class BlockIdChangingEventArgs extends EventArgs {
        public final Identifier newId;
        public boolean canceled = false;
        public BlockIdChangingEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }

    public class BlockIdChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public BlockIdChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }

    public final Event<BlockIdChangingEventArgs> blockIdChanging = new Event<>();
    public final Event<BlockIdChangedEventArgs> blockIdChanged = new Event<>();

    public GhostSlot(Identifier blockId, boolean active) {
        super(0, 0, SLOT_SIZE, SLOT_SIZE);
        this.blockId = blockId;
        this.active = active;
    }

    public void setBlockId(Identifier blockId, boolean invokeEvent) {
        if (invokeEvent) {
            var eventArgs = new BlockIdChangingEventArgs(blockId);
            blockIdChanging.invoke(this, eventArgs);
            if (eventArgs.canceled) return;
        }
        this.blockId = blockId;
        if (invokeEvent) {
            blockIdChanged.invoke(this, new BlockIdChangedEventArgs(blockId));
        }
    }
    public void setBlockId(Identifier blockId) {
        setBlockId(blockId, true);
    }

    public Identifier getBlockId() { 
        return this.blockId;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            int borderColor = active ? 0xFFFFFF00 : 0xFF8B8B8B;
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF222222);
            var t = Registries.ITEM.get(blockId);
            context.drawItem(new ItemStack(t == Items.AIR ? Registries.BLOCK.get(blockId): t), x + 1, y + 1);
        }
    }

    @Override
    public void setFocused(boolean focused) {
        this.active = focused;
        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        active = isMouseOver(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
