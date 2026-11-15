package cn.myfrank.stationbuilder.items;

import cn.myfrank.stationbuilder.scheduler.client.MtrSchedulerClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Item that opens the in-game MTR schedule planner (1.21.x NeoForge).
 *
 * <p>{@link BaseItem} adapts the changing {@code use} signature of 1.21.1 / 1.21.4; this class only
 * provides the behaviour.</p>
 */
public class MtrSchedulerItem extends BaseItem {

    public MtrSchedulerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult handleUse(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            MtrSchedulerClient.openPlanner();
        }
        return InteractionResult.SUCCESS;
    }
}
