package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class GuiScrollablePanel extends GuiPanel {
    protected int scrollOffset = 0;
    protected int maxScroll = 0;
    protected int contentHeight = 0;

    public GuiScrollablePanel(int width, int height) {
        super(width, height);
        this.setBackgroundVisible(true);
        this.setBorderVisible(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        if (showBackground) context.fill(x, y, x + w, y + h, 0x66000000);
        calculateContentHeight();
        maxScroll = Math.max(0, contentHeight - (h - paddingTop - paddingBottom));
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        applyLayout();
        context.enableScissor(x, y, x + w, y + h);
        for (GuiControlLike control : controls) {
            if (!control.isVisible()) continue;
            control.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
        if (maxScroll > 0) {
            int barWidth = 2;
            int barHeight = Math.max(10, (int) ((float) h * h / contentHeight));
            int barY = y + (int) ((float) scrollOffset / maxScroll * (h - barHeight));
            context.fill(x + w - barWidth - 1, barY, x + w - 1, barY + barHeight, 0xFFAAAAAA);
        }

        if (showBorder) context.drawBorder(x, y, w, h, 0xFF8B8B8B);
    }

    private void calculateContentHeight() {
        int totalHeight = paddingTop;
        for (GuiControlLike control : controls) {
            if (control.isVisible()) {
                totalHeight += control.getHeight() + gap;
            }
        }
        this.contentHeight = totalHeight + paddingBottom;
    }

    private void applyLayout() {
        int currentY = y + paddingTop - scrollOffset;
        for (GuiControlLike control : controls) {
            if (!control.isVisible()) continue;
            control.setX(x + paddingLeft);
            control.setY(currentY);
            currentY += control.getHeight() + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset -= (int) (verticalAmount * 15);
            return true;
        }
        return false;
    }
}