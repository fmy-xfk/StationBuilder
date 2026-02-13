package cn.myfrank.stationbuilder.mixin.client.mtr;

import cn.myfrank.stationbuilder.RailBuilderItem;
import cn.myfrank.stationbuilder.StationBuilderBlock;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.mapper.PlayerHelper;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRails.class)
public class RenderRailsMixin {
    @Inject(
            method = "isHoldingRailRelated",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void afterIsHoldingRailRelated(
            ClientPlayerEntity clientPlayerEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean original = cir.getReturnValue();

        // === 你的额外判断 ===
        boolean extra = isHoldingMyCustomRailThing(clientPlayerEntity);

        // === 整合结果 ===
        cir.setReturnValue(original || extra);
    }

    @Unique
    private static boolean isHoldingMyCustomRailThing(ClientPlayerEntity clientPlayerEntity) {
        return PlayerHelper.isHolding(new PlayerEntity(clientPlayerEntity.data), (item) ->
                item.data instanceof RailBuilderItem ||
                net.minecraft.block.Block.getBlockFromItem(item.data) instanceof StationBuilderBlock
        );
    }
}
