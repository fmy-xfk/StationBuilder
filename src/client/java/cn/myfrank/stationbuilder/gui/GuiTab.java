package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class GuiTab extends GuiPanel {
    private final GuiPanel headerPanel;
    private final GuiPanel contentContainer;
    private final List<GuiControlLike> tabContents = new ArrayList<>();
    private final List<GuiButton> tabButtons = new ArrayList<>();
    private int selectedIndex = -1;

    // 样式配置
    private int headerHeight = 18;
    private int tabButtonWidth = 50;

    public int getContentWidth() { return contentContainer.getWidth(); }
    public int getContentHeight() { return contentContainer.getHeight(); }

    public GuiTab(int width, int height) {
        super(width, height);
        // 主面板设为垂直布局：上方是标题栏，下方是内容
        this.setDirection(PanelDirection.VERTICAL);
        this.setGap(2);

        // 初始化标题栏
        headerPanel = new GuiPanel(width, headerHeight);
        headerPanel.setDirection(PanelDirection.HORIZONTAL);
        headerPanel.setGap(2);
        headerPanel.setCrossAlign(CrossAlignMode.END); // 按钮对齐底部

        // 初始化内容容器
        contentContainer = new GuiPanel(width, height - headerHeight - getGap());
        contentContainer.setBorderVisible(true);
        contentContainer.setBackgroundVisible(true);
        contentContainer.setPadding(0);

        // 添加到主面板
        this.addControl(headerPanel);
        this.addControl(contentContainer);
    }

    /**
     * 添加一个选项卡
     * @param title 选项卡显示的文字
     * @param content 该选项卡对应的内容面板/控件
     */
    public void addTab(Text title, GuiControlLike content) {
        int index = tabContents.size();
        tabContents.add(content);

        // 创建切换按钮
        GuiButton button = new GuiButton(title, btn -> {
            selectTab(index);
        }, tabButtonWidth, headerHeight);

        tabButtons.add(button);
        headerPanel.addControl(button);

        // 将内容添加到容器中，初始设为不可见
        content.setVisible(false);
        contentContainer.addControl(content);

        // 如果是第一个添加的，默认选中
        if (selectedIndex == -1) {
            selectTab(0);
        }
    }

    /**
     * 切换到指定的选项卡
     */
    public void selectTab(int index) {
        if (index < 0 || index >= tabContents.size()) return;

        this.selectedIndex = index;

        for (int i = 0; i < tabContents.size(); i++) {
            boolean isSelected = (i == index);
            // 切换内容的可见性
            tabContents.get(i).setVisible(isSelected);
            // 切换按钮的状态（选中的按钮禁用，或者你可以自定义颜色）
            tabButtons.get(i).setActive(!isSelected);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setTabButtonWidth(int tabButtonWidth) {
        this.tabButtonWidth = tabButtonWidth;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先调用父类进行布局计算和渲染
        super.render(context, mouseX, mouseY, delta);
    }
}