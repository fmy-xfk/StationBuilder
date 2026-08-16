package cn.myfrank.stationbuilder;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID)
public final class ForgeServerEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        TickScheduler.tick();
    }
}
