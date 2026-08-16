package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickScheduler {
    private static final List<DelayedTask> tasks = new ArrayList<>();
    private static final List<Runnable> toRun = new ArrayList<>();
    public static void init() {}
    public static void tick() {
        Iterator<DelayedTask> it = tasks.iterator();
        while (it.hasNext()) {
            DelayedTask task = it.next();
            if (--task.remainingTicks <= 0) { toRun.add(task.action); it.remove(); }
        }
        for (Runnable action : toRun) action.run();
        toRun.clear();
    }
    public static void schedule(int ticks, Runnable action) { tasks.add(new DelayedTask(ticks, action)); }
    private static class DelayedTask { int remainingTicks; Runnable action; DelayedTask(int ticks,Runnable action){this.remainingTicks=ticks;this.action=action;} }
}
