package cn.myfrank.stationbuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.network.PacketDistributor;

public class BuildingPlacerItem extends Item {
    public BuildingPlacerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                PacketDistributor.sendToPlayer(
                        (ServerPlayer) player,
                        new StationBuilder.SyncOpenPlacerPayload(cfg.toNbt())
                );
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            ServerLevel serverWorld = (ServerLevel) level;
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

                placementData.addProcessor(new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
                    @javax.annotation.Nullable
                    @Override
                    public StructureTemplate.StructureBlockInfo processBlock(
                            net.minecraft.world.level.LevelReader world,
                            BlockPos pos,
                            BlockPos pivot,
                            StructureTemplate.StructureBlockInfo original,
                            StructureTemplate.StructureBlockInfo current,
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

                // === 保存放置前的环境快照 ===
                Vec3i size = template.getSize();
                Map<BlockPos, PlacerHistoryManager.SavedBlockState> savedBlocks = new HashMap<>();
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 0; y < size.getY(); y++) {
                        for (int z = 0; z < size.getZ(); z++) {
                            BlockPos localPos = new BlockPos(x, y, z);
                            BlockPos worldPos = StructureTemplate.transform(localPos, Mirror.NONE, cfg.rotation, BlockPos.ZERO).offset(targetPos);

                            BlockState state = serverWorld.getBlockState(worldPos);
                            CompoundTag nbt = null;
                            var be = serverWorld.getBlockEntity(worldPos);
                            if (be != null) {
                                nbt = be.saveWithFullMetadata(serverWorld.registryAccess());
                            }
                            savedBlocks.put(worldPos, new PlacerHistoryManager.SavedBlockState(state, nbt));
                        }
                    }
                }
                PlacerHistoryManager.push((ServerPlayer) player, serverWorld, savedBlocks);

                template.placeInWorld(serverWorld, targetPos, BlockPos.ZERO, placementData, serverWorld.random, 2);
                player.displayClientMessage(Component.translatable("message.stationbuilder.placed_building", cfg.presetName)
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                player.displayClientMessage(Component.translatable("message.stationbuilder.template_not_found", cfg.presetName)
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                PacketDistributor.sendToPlayer(
                        (ServerPlayer) player,
                        new StationBuilder.SyncOpenPlacerPayload(BuildingPlacerConfig.fromItem(stack).toNbt())
                );
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_placer_line1"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_placer_line2"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_placer_line3"));
    }
}
