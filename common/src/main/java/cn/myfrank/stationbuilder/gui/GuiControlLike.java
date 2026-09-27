package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import org.jetbrains.annotations.Nullable;

public interface GuiControlLike extends Renderable, GuiEventListener, NarratableEntry {
    int getWidth();
    int getHeight();
    void setX(int x);
    void setY(int y);
    int getX();
    int getY();
    default void setPosition(int x, int y) {
        setX(x); setY(y);
    }
    boolean isVisible();
    void setVisible(boolean visible);

    @Nullable
    GuiControlLike getParent();

    void setParent(@Nullable GuiControlLike parent);
}
