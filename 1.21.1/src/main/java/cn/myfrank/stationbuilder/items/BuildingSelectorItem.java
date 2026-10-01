package cn.myfrank.stationbuilder.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BuildingSelectorItem extends BuildingSelectorItemBase {
    public BuildingSelectorItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        ActionResult result = handleUse(world, user, hand);
        return result == ActionResult.SUCCESS ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
    }
}
