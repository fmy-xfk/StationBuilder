package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class GhostSlot extends GuiControl implements GhostSlotLike {
    protected static final int SLOT_SIZE = 18;
    private Identifier blockId;

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

    @Override
    public void setBlockId(Identifier blockId) {
        setBlockId(blockId, true);
    }

    public Identifier getBlockId() {
        return this.blockId;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            int borderColor = hovered ? 0xFFFFFFFF : 0xFF8B8B8B;
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF222222);

            var t = Registries.ITEM.get(blockId);
            context.drawItem(new ItemStack(t == Items.AIR ? Registries.BLOCK.get(blockId): t), x + 1, y + 1);
            if (hovered) {
                context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x88FFFFFF); // 高亮
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            GuiScreen screen = (GuiScreen) MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                if (!screen.cursorStack.isEmpty()) {
                    Identifier newId = Registries.ITEM.getId(screen.cursorStack.getItem());
                    if (screen.cursorStack.getItem() instanceof net.minecraft.item.BlockItem bi) {
                        newId = Registries.BLOCK.getId(bi.getBlock());
                    }
                    setBlockId(newId);
                } else if (button == 1 || button == 2) {
                    setBlockId(new Identifier("minecraft", "air"));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}