package cn.myfrank.stationbuilder.screens;

import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Disclaimer shown before the MTR schedule planner.
 *
 * <p>The planner only analyses platform occupancy. Making that explicit before the first screen
 * avoids the impression that a clean conflict list means the whole railway is conflict-free.</p>
 */
public class MtrSchedulerIntroScreen extends Screen {

    private static final int COLOR_BG = 0xF00E1217;
    private static final int COLOR_PANEL = 0xFF1A2029;
    private static final int COLOR_BORDER = 0xFF37414C;
    private static final int COLOR_TEXT = 0xFFE6ECF2;
    private static final int COLOR_TEXT_DIM = 0xFF95A3B2;
    private static final int COLOR_ACCENT = 0xFF4DA3FF;
    private static final int COLOR_WARNING = 0xFFFFC857;
    private static final int COLOR_BUTTON = 0xFF262F3B;
    private static final int COLOR_BUTTON_HOVER = 0xFF34425A;

    private boolean skipNotice;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int continueX;
    private int cancelX;
    private int buttonY;
    private int toggleX;
    private int toggleY;
    private int toggleW;

    public MtrSchedulerIntroScreen() {
        super(Text.translatable("gui.stationbuilder.scheduler.intro.title"));
        skipNotice = SchedulerSession.get().isIntroDismissed();
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 24);
        panelH = Math.min(210, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        buttonY = panelY + panelH - 26;
        continueX = panelX + panelW - 150;
        cancelX = panelX + panelW - 72;
        toggleX = panelX + 14;
        toggleY = buttonY - 18;
        toggleW = panelW - 28;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COLOR_BG);
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        border(context, panelX, panelY, panelW, panelH, COLOR_BORDER);

        int x = panelX + 14;
        int y = panelY + 12;
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.intro.title").getString(),
                x, y, COLOR_ACCENT);
        y += 14;
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.intro.subtitle").getString(),
                x, y, COLOR_TEXT_DIM);
        y += 16;

        drawText(context, Text.translatable("gui.stationbuilder.scheduler.intro.warning").getString(),
                x, y, COLOR_WARNING);
        y += 14;

        int lineWidth = panelW - 28;
        for (String key : new String[]{
                "gui.stationbuilder.scheduler.intro.line1",
                "gui.stationbuilder.scheduler.intro.line2",
                "gui.stationbuilder.scheduler.intro.line3",
                "gui.stationbuilder.scheduler.intro.line4"
        }) {
            for (String line : wrap(Text.translatable(key).getString(), lineWidth)) {
                drawText(context, line, x, y, COLOR_TEXT_DIM);
                y += 10;
            }
            y += 2;
        }

        boolean toggleHovered = inside(mouseX, mouseY, toggleX, toggleY, toggleW, 11);
        context.fill(toggleX, toggleY, toggleX + 9, toggleY + 9, skipNotice ? COLOR_ACCENT : COLOR_BUTTON);
        border(context, toggleX, toggleY, 9, 9, toggleHovered ? COLOR_ACCENT : COLOR_BORDER);
        if (skipNotice) {
            drawText(context, "x", toggleX + 2, toggleY + 1, 0xFF10141A);
        }
        drawText(context, Text.translatable("gui.stationbuilder.scheduler.intro.skip").getString(),
                toggleX + 14, toggleY + 1, toggleHovered ? COLOR_TEXT : COLOR_TEXT_DIM);

        button(context, continueX, buttonY, 72, 18,
                Text.translatable("gui.stationbuilder.scheduler.intro.continue").getString(),
                true, mouseX, mouseY);
        button(context, cancelX, buttonY, 58, 18,
                Text.translatable("gui.stationbuilder.scheduler.intro.cancel").getString(),
                true, mouseX, mouseY);
    }

    private void button(
            DrawContext context,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean enabled,
            int mouseX,
            int mouseY
    ) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        context.fill(x, y, x + w, y + h, enabled ? (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON) : COLOR_BUTTON);
        border(context, x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BORDER);
        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        int textY = y + (h - 9) / 2 + 1;
        drawText(context, label, textX, textY, enabled ? (hovered ? COLOR_ACCENT : COLOR_TEXT) : COLOR_TEXT_DIM);
    }

    private void drawText(DrawContext context, String text, int x, int y, int color) {
        if (text != null && !text.isEmpty()) {
            context.drawText(textRenderer, text, x, y, color, false);
        }
    }

    private static void border(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String paragraph : text.split("\n")) {
            String remaining = paragraph.trim();
            while (!remaining.isEmpty()) {
                int end = 0;
                int width = 0;
                int lastSpace = -1;
                while (end < remaining.length()) {
                    int codePoint = remaining.codePointAt(end);
                    String character = new String(Character.toChars(codePoint));
                    int charWidth = textRenderer.getWidth(character);
                    if (width + charWidth > maxWidth) {
                        break;
                    }
                    width += charWidth;
                    if (character.equals(" ")) {
                        lastSpace = end;
                    }
                    end += Character.charCount(codePoint);
                }
                if (end >= remaining.length()) {
                    lines.add(remaining);
                    break;
                }
                int cut = lastSpace > 0 ? lastSpace : end;
                if (cut <= 0) {
                    cut = end;
                }
                lines.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(cut).trim();
            }
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(mouseX, mouseY, toggleX, toggleY, toggleW, 11)) {
            skipNotice = !skipNotice;
            return true;
        }
        if (inside(mouseX, mouseY, continueX, buttonY, 72, 18)) {
            openPlanner();
            return true;
        }
        if (inside(mouseX, mouseY, cancelX, buttonY, 58, 18)) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            openPlanner();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openPlanner() {
        SchedulerSession.get().setIntroDismissed(skipNotice);
        MinecraftClient.getInstance().setScreen(new MtrSchedulerScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
