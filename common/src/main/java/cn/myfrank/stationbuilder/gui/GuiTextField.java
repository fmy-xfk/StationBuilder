package cn.myfrank.stationbuilder.gui;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.function.Predicate;

public class GuiTextField extends GuiControl {
    public class TextChangedEventArgs extends EventArgs {
        public final String newText;
        public TextChangedEventArgs(String newText) {
            this.newText = newText;
        }
    }
    public final Event<TextChangedEventArgs> textChanged = new Event<>();
    protected TextRenderer textRenderer;
    protected String text;
    protected int maxLength = 32; // 默认最大长度
    protected boolean editable = true;
    protected Predicate<String> textPredicate = s -> true; // 文本验证器
    protected int textColor = 0xFFE0E0E0;
    protected int disabledTextColor = 0xFF707070;
    protected int backgroundColor = 0xFF000000;
    protected int borderColor = 0xFFA0A0A0;
    protected int focusedBorderColor = 0xFFFFFFA0;

    protected int selectionStart; // 文本选择开始位置
    protected int selectionEnd;   // 文本选择结束位置
    protected int cursor;         // 光标位置
    protected long lastBlinkTime;     // 光标闪烁计时
    protected boolean numberOnly = false; // 是否仅允许数字输入

    // 新增：文本滚动偏移量，表示文本内容向左滚动的像素值
    protected int scrollOffset = 0;

    // 文本内容区域的左右边距
    protected static final int TEXT_PADDING_X = 4;

    public GuiTextField(TextRenderer textRenderer, int width, int height, Text initialText) {
        super(0, 0, width, height); // x, y 设为 0，由 GuiPanel 布局
        this.textRenderer = textRenderer;
        this.text = initialText.getString();
        this.cursor = this.text.length();
        this.selectionStart = this.cursor;
        this.selectionEnd = this.cursor;
        this.h = height; // 确保高度被设置
        this.lastBlinkTime = System.currentTimeMillis(); // 初始化光标闪烁时间
        this.updateScrollOffset(); // 初始化滚动偏移量
    }

    public GuiTextField(TextRenderer textRenderer, int width, int height) {
        this(textRenderer, width, height, Text.empty());
    }

    public void setNumberOnly(boolean numberOnly) {this.numberOnly = numberOnly;}

    public boolean isNumberOnly() {return this.numberOnly;}

    public String getText() {return this.text;}

    public void setText(String newText) {
        setText(newText, true);
    }

    public void setText(String newText, boolean invokeEvent) {
        if (textPredicate.test(newText)) {
            if (newText.length() > maxLength) {
                newText = newText.substring(0, maxLength);
            }
            this.text = newText;
            // 确保光标位置在文本范围内
            this.cursor = Math.max(0, Math.min(this.cursor, this.text.length()));
            this.selectionStart = this.cursor;
            this.selectionEnd = this.cursor;
            this.lastBlinkTime = System.currentTimeMillis(); // 刷新闪烁
            this.updateScrollOffset(); // 更新滚动偏移
            if(invokeEvent) {
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
        }
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        // 如果当前文本超过新设定的最大长度，则截断
        if (this.text.length() > maxLength) {
            this.setText(this.text.substring(0, maxLength));
        }
    }

    public void setEditable(boolean editable) {this.editable = editable;}
    public void setTextColor(int textColor) {this.textColor = textColor;}
    public void setDisabledTextColor(int disabledTextColor) {this.disabledTextColor = disabledTextColor;}
    public void setTextPredicate(Predicate<String> textPredicate) {this.textPredicate = textPredicate;}
    // 移动光标
    protected void moveCursor(int offset) {this.setCursor(this.cursor + offset);}
    // 设置光标位置
    protected void setCursor(int newCursor) {
        newCursor = Math.max(0, Math.min(newCursor, this.text.length()));
        this.cursor = newCursor;
        this.selectionStart = newCursor;
        this.selectionEnd = newCursor;
        this.lastBlinkTime = System.currentTimeMillis();
        this.updateScrollOffset(); // 光标移动后更新滚动偏移
    }

    // 将光标设置到文本末尾
    protected void setCursorToEnd() {this.setCursor(this.text.length());}

    // 删除选择区域的文本
    protected void deleteSelectedText() {
        if (selectionStart != selectionEnd) {
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            this.text = new StringBuilder(this.text).delete(start, end).toString();
            this.setCursor(start); // 还原光标位置到删除区域的起点
            textChanged.invoke(this, new TextChangedEventArgs(this.text));
        }
    }

    // 插入文本
    protected void insertText(String insertion) {
        if (!editable) return;
        deleteSelectedText(); // 如果有选择区域，先删除
        StringBuilder builder = new StringBuilder(this.text);
        builder.insert(cursor, insertion);
        String newText = SharedConstants.stripInvalidChars(builder.toString()); // 过滤非法字符

        if (textPredicate.test(newText) && newText.length() <= maxLength) {
            this.text = newText;
            this.setCursor(this.cursor + insertion.length());
            textChanged.invoke(this, new TextChangedEventArgs(this.text));
        }
    }

    // 获取当前选中的文本
    public String getSelectedText() {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        return this.text.substring(start, end);
    }

    /**
     * 根据鼠标点击的X坐标计算光标应该放置的位置。
     * @param mouseXAbs 鼠标在屏幕上的X坐标
     * @return 光标位置（文本索引）
     */
    protected int getCursorPosFromMouseX(int mouseXAbs) {
        // 计算鼠标相对于文本渲染区域的局部X坐标
        int localMouseX = mouseXAbs - (x + TEXT_PADDING_X);

        // 将局部X坐标加上滚动偏移量，得到鼠标在完整文本中的“虚拟”X坐标
        int virtualMouseX = localMouseX + scrollOffset;

        // 获取完整文本的子字符串，以便计算宽度
        String fullText = this.text;
        int currentWidth = 0;
        for (int i = 0; i < fullText.length(); i++) {
            int charWidth = textRenderer.getWidth(fullText.substring(i, i + 1));
            if (virtualMouseX >= currentWidth && virtualMouseX < currentWidth + charWidth / 2) {
                return i;
            }
            if (virtualMouseX >= currentWidth + charWidth / 2 && virtualMouseX < currentWidth + charWidth) {
                return i + 1;
            }
            currentWidth += charWidth;
        }
        return fullText.length(); // 如果点击在文本末尾之后
    }

    protected void updateScrollOffset() {
        int textRenderWidth = w - TEXT_PADDING_X * 2; // 文本框内容的可用宽度

        if (this.text.isEmpty()) {
            this.scrollOffset = 0;
            return;
        }

        // 获取光标之前的文本宽度
        String textBeforeCursor = this.text.substring(0, cursor);
        int cursorPixelPos = textRenderer.getWidth(textBeforeCursor); // 光标在完整文本中的像素位置

        // 如果光标在可见区域左侧，则向左滚动
        if (cursorPixelPos < scrollOffset) {
            scrollOffset = cursorPixelPos;
        }
        // 如果光标在可见区域右侧，则向右滚动
        else if (cursorPixelPos > scrollOffset + textRenderWidth) {
            scrollOffset = cursorPixelPos - textRenderWidth;
        }

        // 确保滚动偏移量不会超出文本总宽度
        int totalTextWidth = textRenderer.getWidth(this.text);
        if (totalTextWidth < textRenderWidth) { // 文本没填满，不需要滚动
            scrollOffset = 0;
        } else {
            // 确保不会滚过头，导致右边出现空白
            scrollOffset = Math.min(scrollOffset, totalTextWidth - textRenderWidth);
            // 确保不会滚到负数
            scrollOffset = Math.max(0, scrollOffset);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        int currentTextColor = editable ? textColor : disabledTextColor;
        int currentBorderColor = isFocused() ? focusedBorderColor : borderColor;

        context.fill(x, y, x + w, y + h, backgroundColor);
        context.drawBorder(x, y, w, h, currentBorderColor);

        // 渲染文本和光标
        int textRenderAreaX = x + TEXT_PADDING_X;
        int textRenderAreaY = y + (h - textRenderer.fontHeight) / 2;
        int textRenderAreaWidth = w - TEXT_PADDING_X * 2; // 可用于渲染文本的宽度

        // 裁剪渲染区域，确保文本不会溢出文本框
        context.enableScissor(textRenderAreaX, y, textRenderAreaX + textRenderAreaWidth, y + h);
        String fullText = this.text;
        // 计算选中文本的渲染位置
        if (selectionStart != selectionEnd) {
            int selMin = Math.min(selectionStart, selectionEnd);
            int selMax = Math.max(selectionStart, selectionEnd);

            int selStartX = textRenderer.getWidth(fullText.substring(0, selMin));
            int selEndX = textRenderer.getWidth(fullText.substring(0, selMax));

            // 根据滚动偏移量调整渲染坐标
            context.fill(
                    textRenderAreaX + selStartX - scrollOffset,
                    textRenderAreaY - 1,
                    textRenderAreaX + selEndX - scrollOffset,
                    textRenderAreaY + textRenderer.fontHeight + 1,
                    0x880000FF // 半透明蓝色
            );
        }

        // 绘制文本
        context.drawText(
                textRenderer,
                fullText,
                textRenderAreaX - scrollOffset, // 应用滚动偏移
                textRenderAreaY,
                currentTextColor,
                false
        );

        // 绘制光标
        if (isFocused() && editable && ((System.currentTimeMillis() - lastBlinkTime) / 500) % 2 == 0) {
            int cursorPixelX = textRenderer.getWidth(fullText.substring(0, cursor));
            context.fill(
                    textRenderAreaX + cursorPixelX - scrollOffset, // 应用滚动偏移
                    textRenderAreaY - 1,
                    textRenderAreaX + cursorPixelX - scrollOffset + 1,
                    textRenderAreaY + textRenderer.fontHeight + 1,
                    currentTextColor
            );
        }
        context.disableScissor(); // 禁用裁剪
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && isMouseOver(mouseX, mouseY) && button == 0) { // 左键点击
            setFocused(true);
            this.lastBlinkTime = System.currentTimeMillis();

            this.setCursor(getCursorPosFromMouseX((int)mouseX)); // 使用修正后的方法
            return true;
        }
        setFocused(false); // 点击到外面则失去焦点
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !editable) return false;
        lastBlinkTime = System.currentTimeMillis(); // 每次按键都重置光标闪烁

        // 处理方向键和删除键
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (hasSelection()) { // 如果有选中，光标移动到选择起点
                setCursor(Math.min(selectionStart, selectionEnd));
            } else {
                moveCursor(-1);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (hasSelection()) { // 如果有选中，光标移动到选择终点
                setCursor(Math.max(selectionStart, selectionEnd));
            } else {
                moveCursor(1);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            setCursor(0);
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            setCursorToEnd();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelectedText();
            } else if (cursor > 0) {
                this.text = new StringBuilder(this.text).deleteCharAt(cursor - 1).toString();
                this.setCursor(cursor - 1);
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelectedText();
            } else if (cursor < text.length()) {
                this.text = new StringBuilder(this.text).deleteCharAt(cursor).toString();
                this.setCursor(cursor); // 调用 setCursor 会更新滚动偏移 (光标位置不变)
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+A 全选
            this.selectionStart = 0;
            this.selectionEnd = text.length();
            this.cursor = text.length(); // 光标也移到末尾
            this.updateScrollOffset(); // 更新滚动以确保选择区域可见
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+C 复制
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_X && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+X 剪切
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            deleteSelectedText();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+V 粘贴
            String clipboardText = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboardText != null && !clipboardText.isEmpty()) insertText(clipboardText);
            return true;
        }
        return false;
    }
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || !editable) return false;
        if (SharedConstants.isValidChar(chr) && (!numberOnly || Character.isDigit(chr))) {
            insertText(String.valueOf(chr));
            return true;
        }
        return false;
    }
    protected boolean hasSelection() {return selectionStart != selectionEnd;}
    @Override public void setPosition(int x, int y) {super.setPosition(x, y);}
    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        if (isFocused()) return GuiNavigationPath.of(this);
        return null;
    }
    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        if (this.editable && this.isVisible()) return GuiNavigationPath.of(this);
        return null;
    }
    @Override public ScreenRect getNavigationFocus() {return new ScreenRect(this.x, this.y, this.w, this.h);}
    @Override public boolean isNarratable() {return this.isVisible() && this.editable;}
}