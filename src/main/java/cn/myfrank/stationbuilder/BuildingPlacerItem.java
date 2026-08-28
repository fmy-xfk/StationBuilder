package cn.myfrank.stationbuilder;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class BuildingPlacerItem extends Item {
    public BuildingPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);

        if (player.isCrouching()) {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                PlatformServices.sendToPlayer(serverPlayer, StationBuilder.PACKET_SYNC_OPEN_PLACER,
                        StationBuilder.buf(buf -> buf.writeNbt(cfg.toNbt())));
            }
            return InteractionResult.SUCCESS;
        }

        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverWorld = serverPlayer.serverLevel();
            BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace());

            var templateOpt = BuildingTemplateManager.getTemplate(cfg.presetName);
            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                StructurePlaceSettings placementData = new StructurePlaceSettings()
                        .setRotation(cfg.rotation)
                        .setMirror(Mirror.NONE);

                if (!cfg.placeAir) {
                    placementData.addProcessor(BlockIgnoreProcessor.AIR);
                }

                placementData.addProcessor(new StructureProcessor() {
                    @Override
                    public StructureBlockInfo processBlock(
                            net.minecraft.world.level.LevelReader world,
                            BlockPos pos,
                            BlockPos pivot,
                            StructureBlockInfo original,
                            StructureBlockInfo current,
                            StructurePlaceSettings placementData
                    ) {
                        BlockState rotatedState = BlockRotationUtil.forceRotateState(current.state(), placementData.getRotation());
                        return new StructureTemplate.StructureBlockInfo(current.pos(), rotatedState, current.nbt());
                    }

                    @Override
                    protected StructureProcessorType<?> getType() {
                        return null;
                    }
                });

                Vec3i size = template.getSize();
                Map<BlockPos, PlacerHistoryManager.SavedBlockState> savedBlocks = new HashMap<>();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos localPos = new BlockPos(x, y, z);
                            BlockPos worldPos = StructureTemplate.calculateRelativePosition(placementData, localPos).offset(targetPos);

                            BlockState state = serverWorld.getBlockState(worldPos);
                            CompoundTag nbt = null;
                            var be = serverWorld.getBlockEntity(worldPos);
                            if (be != null) {
                                nbt = be.saveWithFullMetadata();
                            }
                            savedBlocks.put(worldPos, new PlacerHistoryManager.SavedBlockState(state, nbt));
                        }
                    }
                }
                PlacerHistoryManager.push(serverPlayer, serverWorld, savedBlocks);

                template.placeInWorld(serverWorld, targetPos, BlockPos.ZERO, placementData, serverWorld.random, 2);
                player.sendSystemMessage(Component.translatable("message.stationbuilder.placed_building", cfg.presetName)
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.translatable("message.stationbuilder.template_not_found", cfg.presetName)
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user.isCrouching()) {
            if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
                PlatformServices.sendToPlayer(serverPlayer, StationBuilder.PACKET_SYNC_OPEN_PLACER,
                        StationBuilder.buf(buf -> buf.writeNbt(BuildingPlacerConfig.fromItem(stack).toNbt())));
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
