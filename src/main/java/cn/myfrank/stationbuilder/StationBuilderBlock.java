package cn.myfrank.stationbuilder;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StationBuilderBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<StationBuilderBlock> CODEC = createCodec(StationBuilderBlock::new);

    public StationBuilderBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof StationBuilderBlockEntity builderBe) {
                NbtCompound nbt = new NbtCompound();
                builderBe.writeNbt(nbt, world.getRegistryManager());

                ServerPlayNetworking.send(
                        (ServerPlayerEntity) player,
                        new StationBuilder.SyncOpenStationPayload(
                                pos,
                                state.get(FACING).getHorizontalQuarterTurns(),
                                nbt
                        )
                );
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (world.isClient) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof StationBuilderBlockEntity builderBe) {
            NbtComponent component = itemStack.get(ModComponents.STATION_BUILDER_DATA);
            if (component != null) {
                builderBe.readNbt(component.copyNbt(), world.getRegistryManager());
                builderBe.markDirty();
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StationBuilderBlockEntity(pos, state);
    }
}