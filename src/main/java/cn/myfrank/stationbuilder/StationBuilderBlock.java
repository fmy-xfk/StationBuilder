package cn.myfrank.stationbuilder;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static cn.myfrank.stationbuilder.StationBuilder.SYNC_AND_OPEN_PACKET;

public class StationBuilderBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public StationBuilderBlock(Settings settings) {
        super(settings);
        setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
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
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof StationBuilderBlockEntity builderBe) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(pos);
                buf.writeInt(state.get(FACING).getHorizontal());

                // 写入 BE 数据
                NbtCompound nbt = new NbtCompound();
                builderBe.writeNbt(nbt);
                buf.writeNbt(nbt);

                ServerPlayNetworking.send((ServerPlayerEntity) player, SYNC_AND_OPEN_PACKET, buf);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        // 放置时：从 ItemStack 的 NBT 恢复到 BlockEntity
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof StationBuilderBlockEntity builderBe && itemStack.hasNbt()) {
                // 获取物品中存储的 "BlockEntityTag"
                NbtCompound blockEntityTag = itemStack.getSubNbt("BlockEntityTag");
                if (blockEntityTag != null) {
                    builderBe.readNbt(blockEntityTag);
                    builderBe.markDirty();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        BlockEntity be = builder.getOptional(LootContextParameters.BLOCK_ENTITY);

        if (be instanceof StationBuilderBlockEntity builderBe) {
            for (ItemStack stack : drops) {
                if (stack.getItem() == ModBlocks.STATION_BUILDER_ITEM) {
                    // 将 BE 数据写入物品的 Nbt
                    NbtCompound nbt = new NbtCompound();
                    builderBe.writeNbt(nbt);
                    // 移除原版的坐标数据，只保留我们自定义的内容
                    nbt.remove("x"); nbt.remove("y"); nbt.remove("z"); nbt.remove("id");
                    stack.setSubNbt("BlockEntityTag", nbt);
                }
            }
        }
        return drops;
    }


    // 实现接口方法：创建新的 BlockEntity 实例
    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StationBuilderBlockEntity(pos, state);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // 如果是创造模式，手动触发一次掉落逻辑
        if (!world.isClient && player.isCreative()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof StationBuilderBlockEntity builderBe) {
                ItemStack stack = new ItemStack(this);
                // 写入数据
                NbtCompound nbt = new NbtCompound();
                builderBe.writeNbt(nbt);
                nbt.remove("x"); nbt.remove("y"); nbt.remove("z"); nbt.remove("id");
                stack.setSubNbt("BlockEntityTag", nbt);

                // 在位置生成掉落物实体
                ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                itemEntity.setToDefaultPickupDelay();
                world.spawnEntity(itemEntity);
            }
        }
    }
}