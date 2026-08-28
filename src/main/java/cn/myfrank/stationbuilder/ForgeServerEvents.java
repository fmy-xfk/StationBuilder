package cn.myfrank.stationbuilder;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID)
public final class ForgeServerEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 判定仅在当前 Tick 结束时运行逻辑（等价于 1.20.4 的 .Post 阶段）
        if (event.phase == TickEvent.Phase.END) {
            TickScheduler.tick();
        }
    }
}