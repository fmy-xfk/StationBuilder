package cn.myfrank.stationbuilder.mixin;

import cn.myfrank.stationbuilder.manager.PlacerHistoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the previous block state before any block is replaced, so the undo
 * history can restore it later. The capture itself is a no-op unless a
 * {@link PlacerHistoryManager.Recording} is currently active.
 */
@Mixin(Level.class)
public abstract class LevelBlockEditMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void stationbuilder$captureBeforeSet(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        PlacerHistoryManager.capture((Level) (Object) this, pos);
    }
}
