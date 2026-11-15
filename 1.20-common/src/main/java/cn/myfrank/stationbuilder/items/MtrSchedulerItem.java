package cn.myfrank.stationbuilder.items;

import cn.myfrank.stationbuilder.scheduler.client.MtrSchedulerClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Item that opens the in-game MTR schedule planner.
 *
 * <p>The planner only reads MTR's web API on the client; using this item never sends a packet and
 * never modifies MTR data on the server.</p>
 */
public class MtrSchedulerItem extends Item {

    public MtrSchedulerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            MtrSchedulerClient.openPlanner();
        }
        return TypedActionResult.success(stack);
    }
}
