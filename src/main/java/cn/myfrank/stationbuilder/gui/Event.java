package cn.myfrank.stationbuilder.gui;

import java.util.ArrayList;
import java.util.List;


// 事件类封装
public class Event<T extends EventArgs> {
    private List<EventHandler<T>> handlers = new ArrayList<>();
    
    // 添加事件处理器
    public void addHandler(EventHandler<T> handler) {
        handlers.add(handler);
    }
    
    // 移除事件处理器
    public void removeHandler(EventHandler<T> handler) {
        handlers.remove(handler);
    }
    
    // 触发事件
    public void fire(Object sender, T e) {
        // 复制列表以防止在迭代过程中修改
        List<EventHandler<T>> copy = new ArrayList<>(handlers);
        for (EventHandler<T> handler : copy) {
            handler.handle(sender, e);
        }
    }
    
    // C#风格的事件调用（invoke）
    public void invoke(Object sender, T e) {
        fire(sender, e);
    }
    
    // 清空所有处理器
    public void clear() {
        handlers.clear();
    }
    
    // 获取处理器数量
    public int count() {
        return handlers.size();
    }
}