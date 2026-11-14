package cn.myfrank.stationbuilder.mixin;

import cn.myfrank.stationbuilder.manager.PlacerHistoryManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the previous block state before any block is replaced, so the undo
 * history can restore it later. The capture itself is a no-op unless a
 * {@link PlacerHistoryManager.Recording} is currently active.
 */
@Mixin(World.class)
public abstract class WorldBlockEditMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void stationbuilder$captureBeforeSet(
            BlockPos pos,
            BlockState state,
            int flags,
            int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir
    ) {
        PlacerHistoryManager.capture((World) (Object) this, pos);
    }
}
