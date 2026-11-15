package cn.myfrank.stationbuilder.scheduler.client;

import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import cn.myfrank.stationbuilder.screens.MtrSchedulerIntroScreen;
import cn.myfrank.stationbuilder.screens.MtrSchedulerScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for the schedule planner.
 *
 * <p>Kept in its own class so dedicated servers never load the {@link MtrSchedulerScreen} class
 * hierarchy: the item only calls this method from a {@code world.isClient} branch.</p>
 */
public final class MtrSchedulerClient {

    private MtrSchedulerClient() {
    }

    /** Opens the planner, showing the platform-occupancy disclaimer first when it was not dismissed. */
    public static void openPlanner() {
        Minecraft client = Minecraft.getInstance();
        if (SchedulerSession.get().isIntroDismissed()) {
            client.setScreen(new MtrSchedulerScreen());
        } else {
            client.setScreen(new MtrSchedulerIntroScreen());
        }
    }
}
