package cn.myfrank.stationbuilder.mixin.client.mtr;

import cn.myfrank.stationbuilder.RailBuilderItem;
import cn.myfrank.stationbuilder.StationBuilderBlock;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.mtr.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRails.class)
public class RenderRailsMixin {
    @Inject(method = "isHoldingRailRelated", at = @At("RETURN"), cancellable = true)
    private static void afterIsHoldingRailRelated(
            LocalPlayer clientPlayerEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(
                cir.getReturnValue() || isHoldingMyCustomRailThing(clientPlayerEntity)
        );
    }

    @Unique
    private static boolean isHoldingMyCustomRailThing(LocalPlayer clientPlayerEntity) {
        return clientPlayerEntity.isHolding((itemStack) -> {
            Item item = itemStack.getItem();
            return item instanceof RailBuilderItem || Block.byItem(item) instanceof StationBuilderBlock;
        });
    }
}