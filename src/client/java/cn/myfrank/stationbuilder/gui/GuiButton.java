package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class GuiButton extends GuiControl {
    // 替换 ButtonWidget.PressAction
    public interface PressAction {
        void onPress(GuiButton button);
    }

    protected Text message;
    protected PressAction onPress;
    protected Tooltip tooltip;
    protected boolean active = true; // 对应原版 active 状态
    protected boolean hovered = false; // 对应原版 isMouseOver 状态
    protected TextRenderer textRenderer;

    public GuiButton(Text message, PressAction onPress, int width, int height) {
        super(0, 0, width, height);
        this.message = message;
        this.onPress = onPress;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    public GuiButton(Text message, PressAction onPress, int width, int height, Tooltip tooltip) {
        this(message, onPress, width, height);
        this.tooltip = tooltip;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }

        this.hovered = isMouseOver(mouseX, mouseY); // 更新悬停状态

        int backgroundColor = active ? (hovered ? 0xFF5555FF : 0xFF3333CC) : 0xFF888888; // 蓝色系，悬停变亮，禁用变灰
        int borderColor = active ? (hovered ? 0xFFFFFFFF : 0xFFAAAAAA) : 0xFF555555;
        int textColor = active ? (hovered ? 0xFFFFFF00 : 0xFFFFFFFF) : 0xFFAAAAAA; // 文本颜色，悬停变黄，禁用变灰

        // 绘制背景
        context.fill(x, y, x + w, y + h, backgroundColor);
        // 绘制边框
        context.drawBorder(x, y, w, h, borderColor);

        // 绘制文本
        int textWidth = textRenderer.getWidth(message);
        int textHeight = textRenderer.fontHeight;
        context.drawText(
                textRenderer,
                message,
                x + (w - textWidth) / 2,
                y + (h - textHeight) / 2,
                textColor,
                false
        );

        // 渲染 tooltip
        if (hovered && tooltip != null) {
            // 不使用tooltip
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && active && isMouseOver(mouseX, mouseY) && button == 0) { // 检查可见性、激活状态、鼠标位置和左键
            if (onPress != null) {
                onPress.onPress(this);
            }
            return true;
        }
        return false;
    }

    // 其他事件处理方法可以简化或直接继承 GuiControl 的默认实现
    // 对于完全自定义的 GuiControl，通常不需要实现所有 Element 接口的方法
    // 这里只保留了核心的 isMouseOver
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    // 由于不再继承 ButtonWidget，这些导航和焦点方法需要根据实际需求重新实现
    // 这里提供一个简化版本，它们不会真正处理复杂的导航
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        // 可以根据 focused 状态改变按钮的渲染样式，例如加粗边框
    }

    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        if (isFocused()) {
            return GuiNavigationPath.of(this);
        }
        return null;
    }

    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        if (this.active && this.isVisible()) {
            return GuiNavigationPath.of(this);
        }
        return null;
    }

    @Override
    public ScreenRect getNavigationFocus() {
        return new ScreenRect(this.x, this.y, this.w, this.h);
    }

    @Override
    public boolean isNarratable() {
        return this.isVisible() && this.active; // 仅当可见且激活时才可叙述
    }

    @Override
    public int getNavigationOrder() {
        return 0; // 默认导航顺序，可能需要更复杂的逻辑来指定
    }
}