package cn.myfrank.stationbuilder.gui;

@FunctionalInterface
public interface EventHandler<T extends EventArgs> {
    void handle(Object sender, T e);
}