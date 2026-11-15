package cn.myfrank.stationbuilder.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class MtrSchedulerItem extends MtrSchedulerItemBase {
    public MtrSchedulerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return handleUse(world, user, hand);
    }
}
