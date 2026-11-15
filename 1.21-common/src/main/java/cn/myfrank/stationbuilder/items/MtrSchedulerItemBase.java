package cn.myfrank.stationbuilder.items;

import cn.myfrank.stationbuilder.scheduler.client.MtrSchedulerClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 1.21.x base of the MTR schedule planner item. Version wrappers implement {@code use} because its
 * return type changed between 1.21.1 and 1.21.4.
 */
public class MtrSchedulerItemBase extends Item {

    public MtrSchedulerItemBase(Settings settings) {
        super(settings);
    }

    protected ActionResult handleUse(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            MtrSchedulerClient.openPlanner();
        }
        return ActionResult.SUCCESS;
    }
}
