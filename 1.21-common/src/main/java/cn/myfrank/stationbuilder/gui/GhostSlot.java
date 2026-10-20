package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.resources.ResourceLocation;

public class GhostSlot extends GuiControl implements GhostSlotLike {
    protected static final int SLOT_SIZE = 18;
    private ResourceLocation blockId;

    public class BlockIdChangingEventArgs extends EventArgs {
        public final ResourceLocation newId;
        public boolean canceled = false;
        public BlockIdChangingEventArgs(ResourceLocation newId) {
            this.newId = newId;
        }
    }

    public class BlockIdChangedEventArgs extends EventArgs {
        public final ResourceLocation newId;
        public BlockIdChangedEventArgs(ResourceLocation newId) {
            this.newId = newId;
        }
    }

    public final Event<BlockIdChangingEventArgs> blockIdChanging = new Event<>();
    public final Event<BlockIdChangedEventArgs> blockIdChanged = new Event<>();

    public GhostSlot(ResourceLocation blockId, boolean active) {
        super(0, 0, SLOT_SIZE, SLOT_SIZE);
        this.blockId = blockId;
    }

    public void setBlockId(ResourceLocation blockId, boolean invokeEvent) {
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
    public void setBlockId(ResourceLocation blockId) {
        setBlockId(blockId, true);
    }

    public ResourceLocation getBlockId() { 
        return this.blockId;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            int borderColor = hovered ? 0xFFFFFFFF : 0xFF8B8B8B;
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF222222);
            
            context.renderItem(CommonUtil.getItemStack(blockId), x + 1, y + 1);
            if (hovered) {
                context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x88FFFFFF); // 高亮
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            GuiScreen screen = (GuiScreen) Minecraft.getInstance().screen;
            if (screen != null) {
                if (!screen.cursorStack.isEmpty()) {
                    ResourceLocation newId = CommonUtil.getItemId(screen.cursorStack.getItem());
                    if (screen.cursorStack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                        newId = CommonUtil.getBlockId(bi.getBlock());
                    }
                    setBlockId(newId);
                } else if (button == 1 || button == 2) {
                    setBlockId(ResourceLocation.fromNamespaceAndPath("minecraft", "air"));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}