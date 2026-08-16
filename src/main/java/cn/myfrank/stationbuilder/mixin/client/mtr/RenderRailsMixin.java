package cn.myfrank.stationbuilder.mixin.client.mtr;

import cn.myfrank.stationbuilder.RailBuilderItem;
import cn.myfrank.stationbuilder.StationBuilderBlock;
import net.minecraft.world.level.block.Block;
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
            cancellable = true,
            remap = false
    )
    private static void afterIsHoldingRailRelated(
            ClientPlayerEntity clientPlayerEntity, // 修改为 MTR 所需的包装类型
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean original = cir.getReturnValue();
        boolean extra = isHoldingMyCustomRailThing(clientPlayerEntity);
        cir.setReturnValue(original || extra);
    }

    @Unique
    private static boolean isHoldingMyCustomRailThing(ClientPlayerEntity clientPlayerEntity) {
        return PlayerHelper.isHolding(new PlayerEntity(clientPlayerEntity.data), (item) ->
                item.data instanceof RailBuilderItem || Block.byItem(item.data) instanceof StationBuilderBlock
        );
    }
}