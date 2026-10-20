package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class GuiButton extends GuiControl {
    // 替换 Button.PressAction
    public interface PressAction {
        void onPress(GuiButton button);
    }

    protected Component message;
    protected PressAction onPress;
    protected Tooltip tooltip;
    protected boolean active = true; // 对应原版 active 状态
    protected boolean hovered = false; // 对应原版 isMouseOver 状态
    protected Font textRenderer;

    public GuiButton(Component message, PressAction onPress, int width, int height) {
        super(0, 0, width, height);
        this.message = message;
        this.onPress = onPress;
        this.textRenderer = Minecraft.getInstance().font;
    }

    public GuiButton(Component message, PressAction onPress, int width, int height, Tooltip tooltip) {
        this(message, onPress, width, height);
        this.tooltip = tooltip;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
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
        context.renderOutline(x, y, w, h, borderColor);

        // 绘制文本
        int textWidth = textRenderer.width(message);
        int textHeight = textRenderer.lineHeight;
        context.drawString(
                textRenderer,
                message,
                x + (w - textWidth) / 2,
                y + (h - textHeight) / 2,
                textColor,
                false
        );
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
    // 对于完全自定义的 GuiControl，通常不需要实现所有 GuiEventListener 接口的方法
    // 这里只保留了核心的 isMouseOver
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    // 由于不再继承 Button，这些导航和焦点方法需要根据实际需求重新实现
    // 这里提供一个简化版本，它们不会真正处理复杂的导航
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        // 可以根据 focused 状态改变按钮的渲染样式，例如加粗边框
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        if (this.active && this.isVisible()) {
            return ComponentPath.path(this);
        }
        return null;
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.x, this.y, this.w, this.h);
    }
}