package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.processor.BlockIgnoreStructureProcessor;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class BuildingPlacerItem extends Item {
    public BuildingPlacerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        ItemStack stack = context.getStack();
        BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);

        if (player.isSneaking()) {
            if (!world.isClient) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeNbt(cfg.toNbt());
                ServerPlayNetworking.send((ServerPlayerEntity) player, StationBuilder.SYNC_AND_OPEN_PACKET_PLACER, buf);
            }
            return ActionResult.SUCCESS;
        }

        if (!world.isClient) {
            ServerWorld serverWorld = (ServerWorld) world;
            BlockPos targetPos = context.getBlockPos().offset(context.getSide());

            var templateOpt = BuildingTemplateManager.getTemplate(cfg.presetName);
            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                StructurePlacementData placementData = new StructurePlacementData()
                        .setRotation(cfg.rotation)
                        .setMirror(BlockMirror.NONE);

                if (!cfg.placeAir) {
                    placementData.addProcessor(BlockIgnoreStructureProcessor.IGNORE_AIR);
                }

                // 强制旋转处理器绑定（兼容普通/非标有向方块与 MTR 轨道节点）
                placementData.addProcessor(new net.minecraft.structure.processor.StructureProcessor() {
                    @Override
                    public net.minecraft.structure.StructureTemplate.StructureBlockInfo process(
                            net.minecraft.world.WorldView world,
                            BlockPos pos,
                            BlockPos pivot,
                            net.minecraft.structure.StructureTemplate.StructureBlockInfo original,
                            net.minecraft.structure.StructureTemplate.StructureBlockInfo current,
                            StructurePlacementData placementData
                    ) {
                        BlockState rotatedState = BlockRotationUtil.forceRotateState(current.state(), placementData.getRotation());
                        return new net.minecraft.structure.StructureTemplate.StructureBlockInfo(current.pos(), rotatedState, current.nbt());
                    }

                    @Override
                    protected StructureProcessorType<?> getType() {
                        return null;
                    }
                });

                // 回退（Undo）环境信息录入快照
                Vec3i size = template.getSize();
                Map<BlockPos, PlacerHistoryManager.SavedBlockState> savedBlocks = new HashMap<>();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos localPos = new BlockPos(x, y, z);
                            BlockPos worldPos = StructureTemplate.transform(placementData, localPos).add(targetPos);

                            BlockState state = serverWorld.getBlockState(worldPos);
                            NbtCompound nbt = null;
                            var be = serverWorld.getBlockEntity(worldPos);
                            if (be != null) {
                                nbt = be.createNbt();
                            }
                            savedBlocks.put(worldPos, new PlacerHistoryManager.SavedBlockState(state, nbt));
                        }
                    }
                }
                PlacerHistoryManager.push((ServerPlayerEntity) player, serverWorld, savedBlocks);

                template.place(serverWorld, targetPos, BlockPos.ORIGIN, placementData, serverWorld.random, 2);
                player.sendMessage(Text.translatable("message.stationbuilder.placed_building", cfg.presetName).formatted(net.minecraft.util.Formatting.GREEN), true);
            } else {
                player.sendMessage(Text.translatable("message.stationbuilder.template_not_found", cfg.presetName).formatted(net.minecraft.util.Formatting.RED), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeNbt(BuildingPlacerConfig.fromItem(stack).toNbt());
                ServerPlayNetworking.send((ServerPlayerEntity) user, StationBuilder.SYNC_AND_OPEN_PACKET_PLACER, buf);
            }
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }
}