package cn.myfrank.stationbuilder;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StationBuilderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public StationBuilderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()); }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        StationBuilder.LOGGER.info("[Block] Right-clicked block at: {}, isClient: {}", pos, world.isClientSide);
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity entity = world.getBlockEntity(pos);
            StationBuilder.LOGGER.info("[Block] Found BlockEntity at pos: {}, type: {}", pos, entity != null ? entity.getClass().getSimpleName() : "null");
            if (entity instanceof StationBuilderBlockEntity be) {
                CompoundTag nbt = new CompoundTag();
                be.saveAdditional(nbt);
                StationBuilder.LOGGER.info("[Block] Preparing to send PACKET_SYNC_OPEN to player");
                PlatformServices.sendToPlayer(serverPlayer, StationBuilder.PACKET_SYNC_OPEN, StationBuilder.buf(b -> {
                    b.writeBlockPos(pos);
                    b.writeInt(state.getValue(FACING).get2DDataValue());
                    b.writeNbt(nbt);
                }));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        if (world.isClientSide) return;
        if (world.getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
            StationBuilderState.loadToBlockEntity(itemStack, be);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof StationBuilderBlockEntity builderBe) {
            for (ItemStack stack : drops) {
                if (stack.getItem() == ModBlocks.STATION_BUILDER_ITEM.get()) StationBuilderState.saveFromBlockEntity(stack, builderBe);
            }
        }
        return drops;
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player.isCreative() && world.getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
            ItemStack stack = new ItemStack(this);
            StationBuilderState.saveFromBlockEntity(stack, be);
            world.addFreshEntity(new ItemEntity(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack));
        }
        super.playerWillDestroy(world, pos, state, player);
    }

    @Nullable
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationBuilderBlockEntity(pos, state); }
}