package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class GhostInventory extends GuiControl {
    protected static final int SLOT_SIZE = 18;
    protected Minecraft client = Minecraft.getInstance();

    public GhostInventory() {
        super(0, 0, 172, 80);
    }

    private void drawSlotBackground(GuiGraphics context, int x, int y) {
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
        context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF373737);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            var inv = client.player.getInventory();
            for (int i = 0; i < 36; i++) {
                int slotX = x + (i % 9) * SLOT_SIZE;
                int slotY = y + (i < 9 ? 58 : (i / 9 - 1) * SLOT_SIZE);

                drawSlotBackground(context, slotX, slotY);
                context.renderItem(inv.getItem(i), slotX + 1, slotY + 1);

                // 悬停高亮
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x88FFFFFF);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && isMouseOver(mouseX, mouseY)) {
            int clickedIndex = -1;
            for (int i = 0; i < 36; i++) {
                int slotX = x + (i % 9) * SLOT_SIZE;
                int slotY = y + (i < 9 ? 58 : (i / 9 - 1) * SLOT_SIZE);
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    clickedIndex = i;
                    break;
                }
            }

            GuiScreen screen = (GuiScreen) client.screen;
            if (screen != null && clickedIndex != -1) {
                ItemStack stack = client.player.getInventory().getItem(clickedIndex);
                if (button == 0) { // 左键
                    if (screen.cursorStack.isEmpty() && !stack.isEmpty()) { // 拿起
                        screen.cursorStack = stack.copy();
                    } else if (!screen.cursorStack.isEmpty()) { // 放下/清空指针
                        screen.cursorStack = ItemStack.EMPTY;
                    }
                } else if (button == 1 || button == 2) { // 右/中键清空
                    screen.cursorStack = ItemStack.EMPTY;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}