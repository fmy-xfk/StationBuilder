package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class GuiPanel extends GuiControl {
    public enum PanelDirection {
        HORIZONTAL, VERTICAL
    }
    public enum MajorAlignMode {
        START, CENTER, END, SPACE_BETWEEN
    }
    public enum CrossAlignMode {
        START, CENTER, END
    }
    protected int gap = 5;
    protected MajorAlignMode majorAlign = MajorAlignMode.START;
    protected CrossAlignMode crossAlign = CrossAlignMode.CENTER;
    protected int marginLeft = 0, marginRight = 0, marginTop = 0, marginBottom = 0;
    protected int paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;
    protected boolean showBorder = false, showBackground = false;
    protected PanelDirection direction =  PanelDirection.HORIZONTAL;
    protected List<GuiControlLike> controls = new ArrayList<>();

    public void setMargin(int margin) {
        marginLeft = margin; marginRight = margin; marginTop = margin; marginBottom = margin;
    }
    public void setPadding(int padding) {
        paddingLeft = padding; paddingRight = padding; paddingTop = padding; paddingBottom = padding;
    }
    public void setMargin(int marginLeft, int marginRight, int marginTop, int marginBottom) {
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
    }
    public void setPadding(int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
    }
    public void setMarginLeft(int marginLeft) { this.marginLeft = marginLeft; }
    public int getMarginLeft() { return  marginLeft; }
    public void setMarginRight(int marginRight) { this.marginRight = marginRight; }
    public int getMarginRight() { return  marginRight; }
    public void setMarginTop(int marginTop) { this.marginTop = marginTop; }
    public int getMarginTop() { return  marginTop; }
    public void setMarginBottom(int marginBottom) { this.marginBottom = marginBottom; }
    public int getMarginBottom() { return  marginBottom; }
    public void setPaddingLeft(int paddingLeft) { this.paddingLeft = paddingLeft; }
    public int getPaddingLeft() { return  paddingLeft; }
    public void setPaddingRight(int paddingRight) { this.paddingRight = paddingRight; }
    public int getPaddingRight() { return  paddingRight; }
    public void setPaddingTop(int paddingTop) { this.paddingTop = paddingTop; }
    public int getPaddingTop() { return  paddingTop; }
    public void setPaddingBottom(int paddingBottom) { this.paddingBottom = paddingBottom; }
    public int getPaddingBottom() { return  paddingBottom; }

    public int getGap() { return gap; }
    public void setGap(int gap) { this.gap = gap; }

    public boolean isBorderVisible() { return showBorder; }
    public void setBorderVisible(boolean showBorder) { this.showBorder = showBorder; }
    public PanelDirection getDirection() { return direction; }
    public void setBackgroundVisible(boolean showBackground) { this.showBackground = showBackground; }
    public boolean isBackgroundVisible() { return showBackground; }
    public void setDirection(PanelDirection direction) { this.direction = direction; }
    public MajorAlignMode getMajorAlign() { return majorAlign; }
    public void setMajorAlign(MajorAlignMode majorAlign) { this.majorAlign = majorAlign; }
    public CrossAlignMode getCrossAlign() { return crossAlign; }
    public void setCrossAlign(CrossAlignMode crossAlign) { this.crossAlign = crossAlign; }

    public GuiPanel(int width, int height) {
        super(0, 0, width, height);
    }

    public GuiPanel addControl(GuiControlLike control) {
        controls.add(control);
        control.setParent(this);
        return this;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            for (GuiControlLike control : controls) {
                control.setFocused(false);
            }
        }
        super.setFocused(focused);
    }

    public List<GuiControlLike> doLayout() {
        int panelContentX = x + marginLeft + paddingLeft;
        int panelContentY = y + marginTop + paddingTop;
        int panelRenderWidth = this.w; // GuiPanel 自身的宽度
        int panelRenderHeight = this.h; // GuiPanel 自身的高度

        // 计算主轴方向上所有可见控件的总尺寸（包括 gap）
        int totalMajorSize = 0;
        int maxCrossSize = 0; // 交叉轴上最大的可见控件尺寸
        List<GuiControlLike> visibleControls = new ArrayList<>(); // 存储可见的控件以便后续布局

        if (direction == PanelDirection.HORIZONTAL) {
            for (GuiControlLike control : controls) {
                if (!control.isVisible()) continue;
                visibleControls.add(control);
                totalMajorSize += control.getWidth();
                maxCrossSize = Math.max(maxCrossSize, control.getHeight());
            }
        } else { // VERTICAL
            for (GuiControlLike control : controls) {
                if (!control.isVisible()) continue;
                visibleControls.add(control);
                totalMajorSize += control.getHeight();
                maxCrossSize = Math.max(maxCrossSize, control.getWidth());
            }
        }

        // 如果没有可见的控件，则无需继续布局和渲染
        if (visibleControls.isEmpty()) {
            return visibleControls;
        }

        // 计算主轴起始位置
        int majorStartPos = 0, majorGap = gap;
        if (direction == PanelDirection.HORIZONTAL) {
            int availableMajorSpace = panelRenderWidth - paddingLeft - paddingRight;
            if(majorAlign == MajorAlignMode.SPACE_BETWEEN && visibleControls.size() > 1) { // 使用 visibleControls.size()
                majorGap = (availableMajorSpace - totalMajorSize) / (visibleControls.size() - 1);
            }else {
                totalMajorSize += (visibleControls.size() - 1) * gap; // 使用 visibleControls.size()
            }
            majorStartPos = switch (majorAlign) {
                case START, SPACE_BETWEEN -> panelContentX;
                case CENTER -> panelContentX + (availableMajorSpace - totalMajorSize) / 2;
                case END -> panelContentX + availableMajorSpace - totalMajorSize;
            };

        } else { // VERTICAL
            int availableMajorSpace = panelRenderHeight - paddingTop - paddingBottom;
            if(majorAlign == MajorAlignMode.SPACE_BETWEEN && visibleControls.size() > 1) { // 使用 visibleControls.size()
                majorGap = (availableMajorSpace - totalMajorSize) / (visibleControls.size() - 1);
            }else {
                totalMajorSize += (visibleControls.size() - 1) * gap; // 使用 visibleControls.size()
            }
            majorStartPos = switch (majorAlign) {
                case START, SPACE_BETWEEN -> panelContentY;
                case CENTER -> panelContentY + (availableMajorSpace - totalMajorSize) / 2;
                case END -> panelContentY + availableMajorSpace - totalMajorSize;
            };
        }

        int currentMajorPos = majorStartPos;
        for (GuiControlLike control : visibleControls) {
            int cx = 0, cy = 0;
            if (direction == PanelDirection.HORIZONTAL) {
                cx = currentMajorPos;
                // 计算交叉轴起始位置
                int availableCrossSpace = panelRenderHeight - paddingTop - paddingBottom;
                cy = switch (crossAlign) {
                    case START -> panelContentY;
                    case CENTER -> panelContentY + (availableCrossSpace - control.getHeight()) / 2;
                    case END -> panelContentY + availableCrossSpace - control.getHeight();
                };
                currentMajorPos += control.getWidth() + majorGap;
            } else { // VERTICAL
                cy = currentMajorPos;
                // 计算交叉轴起始位置
                int availableCrossSpace = panelRenderWidth - paddingLeft - paddingRight;
                cx = switch (crossAlign) {
                    case START -> panelContentX;
                    case CENTER -> panelContentX + (availableCrossSpace - control.getWidth()) / 2;
                    case END -> panelContentX + availableCrossSpace - control.getWidth();
                };
                currentMajorPos += control.getHeight() + majorGap;
            }
            control.setPosition(cx, cy);
        }
        return visibleControls;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 首先检查面板自身的可见性
        if (!isVisible()) return;

        // 计算面板的实际内容区域（包括 padding，不包括 margin）
        int panelRenderWidth = this.w; // GuiPanel 自身的宽度
        int panelRenderHeight = this.h; // GuiPanel 自身的高度

        // 绘制背景和边框
        if (showBackground) {
            context.fill(
                    x + marginLeft,
                    y + marginTop,
                    x + marginLeft + panelRenderWidth,
                    y + marginTop + panelRenderHeight,
                    0x44000000
            );
        }
        if (showBorder) {
            context.drawBorder(
                    x + marginLeft,
                    y + marginTop,
                    panelRenderWidth,
                    panelRenderHeight,
                    0xFF8B8B8B
            );
        }

        if (controls.isEmpty()) { return; }

        context.enableScissor(x, y, x + w, y + h);
        for (GuiControlLike control : doLayout()) {
            control.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
    }

    // 递归处理鼠标点击：从后往前遍历（最上层优先）
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        GuiControlLike clickedControl = null;
        for (int i = controls.size() - 1; i >= 0; i--) {
            GuiControlLike control = controls.get(i);
            if (control.isVisible() && control.mouseClicked(mouseX, mouseY, button)) {
                clickedControl = control;
                break;
            }
        }
        if (clickedControl != null) {
            for (GuiControlLike control : controls) {
                if (control != clickedControl) {
                    control.setFocused(false);
                }
            }
            return true;
        }
        return false;
    }

    // 递归处理滚轮、键盘等
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        for (int i = controls.size() - 1; i >= 0; i--) {
            if (controls.get(i).isVisible() && controls.get(i).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (GuiControlLike control : controls) {
            if (control.isVisible() && control.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (GuiControlLike control : controls) {
            if (control.isVisible() && control.charTyped(chr, modifiers)) return true;
        }
        return false;
    }
}