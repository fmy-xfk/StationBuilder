package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickScheduler {
    private static final List<DelayedTask> tasks = new ArrayList<>();
    private static final List<Runnable> toRun = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            // 1. tick 计数 & 收集要执行的任务
            Iterator<DelayedTask> it = tasks.iterator();
            while (it.hasNext()) {
                DelayedTask task = it.next();
                if (--task.remainingTicks <= 0) {
                    toRun.add(task.action);
                    it.remove();
                }
            }

            // 2. 真正执行（此时 tasks 已不在迭代中）
            for (Runnable action : toRun) {
                action.run();
            }
            toRun.clear();
        });
    }

    public static void schedule(int ticks, Runnable action) {
        tasks.add(new DelayedTask(ticks, action));
    }

    private static class DelayedTask {
        int remainingTicks;
        Runnable action;

        DelayedTask(int ticks, Runnable action) {
            this.remainingTicks = ticks;
            this.action = action;
        }
    }
}
