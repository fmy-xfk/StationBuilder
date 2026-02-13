package cn.myfrank.stationbuilder.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class GhostInventory extends GuiControl {
    protected static final int SLOT_SIZE = 18;
    protected MinecraftClient client = MinecraftClient.getInstance();
    private int selectedIndex = -1;
    private List<GhostSlotLike> ghostSlots = new ArrayList<>();

    public GhostInventory() {
        super(0, 0, 172, 80);
    }

    private void drawSlotBackground(DrawContext context, int x, int y) {
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
        context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF373737);
    }

    public GhostInventory addSlot(GhostSlotLike slot) {
        ghostSlots.add(slot);
        return this;
    }

    private int getActiveSlotIndex() {
        for (int i = 0; i < ghostSlots.size(); i++) {
            if (ghostSlots.get(i).isActive()) {
                return i;
            }
        }
        return -1;
    }

    private void updateSelectedElementBlock(Identifier newId) {
        final int index = getActiveSlotIndex();
        if (index < 0) return;
        GhostSlotLike slot = ghostSlots.get(index);
        slot.setBlockId(newId);
    }

    public int getSelectedIndex() { return selectedIndex; }

    public ItemStack getSelectedItemStack() {
        if (selectedIndex == -1) return ItemStack.EMPTY;
        var inv = client.player.getInventory();
        return inv.getStack(selectedIndex);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            var inv = client.player.getInventory();
            for (int i = 0; i < 36; i++) {
                int slotX = x + (i % 9) * SLOT_SIZE;
                int slotY = y + (i < 9 ? 58 : (i / 9 - 1) * SLOT_SIZE);

                drawSlotBackground(context, slotX, slotY);
                context.drawItem(inv.getStack(i), slotX + 1, slotY + 1);
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x88FFFFFF);
                    selectedIndex = i;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var stack = getSelectedItemStack();
        if (stack.isEmpty()) {
            updateSelectedElementBlock(new Identifier("minecraft", "air"));
        } else if (stack.getItem() instanceof net.minecraft.item.BlockItem bi) {
            updateSelectedElementBlock(
                net.minecraft.registry.Registries.BLOCK.getId(bi.getBlock())
            );
        } else {
            updateSelectedElementBlock(
                net.minecraft.registry.Registries.ITEM.getId(stack.getItem())
            );
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
