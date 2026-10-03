package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import org.jetbrains.annotations.Nullable;

public interface GuiControlLike extends Drawable, Element, Selectable {
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
