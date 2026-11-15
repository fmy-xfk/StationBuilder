package cn.myfrank.stationbuilder.items;

import cn.myfrank.stationbuilder.scheduler.client.MtrSchedulerClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item that opens the in-game MTR schedule planner.
 *
 * <p>The planner only reads MTR's web API on the client; using this item never sends a packet and
 * never modifies MTR data on the server.</p>
 */
public class MtrSchedulerItem extends Item {

    public MtrSchedulerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            MtrSchedulerClient.openPlanner();
        }
        return InteractionResultHolder.success(stack);
    }
}
