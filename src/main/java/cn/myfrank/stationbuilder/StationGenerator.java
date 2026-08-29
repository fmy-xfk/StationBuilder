package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StationGenerator {
    public static void build(ServerPlayer player, ServerLevel world, BlockPos origin, Direction facing, int length, List<StationElement> elements) {
        Direction right = facing.getClockWise();

        int totalWidth = 0;
        int maxConstHeight = 6; // 默认站房火柴盒高度为 6
        for (int i = 0; i < elements.size(); i++) {
            StationElement e = elements.get(i);
            StationElement eLeft = i > 0 ? elements.get(i - 1) : null;
            totalWidth += e.getWidth();
            if (eLeft instanceof TrackElement && e instanceof TrackElement) {
                totalWidth++;
            }
            if (e instanceof PlatformElement p && p.hasCanopy) {
                // 雨棚最高点偏移通常为 1格(2个半砖)，再预留 1-2 格空间
                maxConstHeight = Math.max(maxConstHeight, p.canopyHeight + 2);
            }
        }

        List<BlockPos> mtrRails = new ArrayList<>();
        for (int l = 1; l <= length; l++) {
            for (int w = -4; w < totalWidth + 4; w++) {
                for (int y = 0; y <= maxConstHeight; y++) {
                    BlockPos p = origin.relative(facing, l).relative(right, w).above(y);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;

                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                        mtrRails.add(p);
                    } else {
                        // 使用 flag 2 (UPDATE_CLIENTS) 且不包含 flag 1 (UPDATE_NEIGHBORS) 抑制更新，再加 FORCE_STATE 强制覆盖
                        world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        for (BlockPos p : mtrRails) {
            BlockState state = world.getBlockState(p);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                state.getBlock().playerWillDestroy(world, p, state, player);
                world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        BlockPos currentLeftEdge = origin.relative(facing);
        for (int i = 0; i < elements.size(); i++) {
            StationElement element = elements.get(i);
            if (element instanceof TrackElement track) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                if (leftNeighbor instanceof TrackElement) {
                    fillTrackGap(world, currentLeftEdge, facing, right, length, track);
                    currentLeftEdge = currentLeftEdge.relative(right);
                }
                generateTrack(player, world, currentLeftEdge, facing, right, length, track);
            } else if (element instanceof PlatformElement platform) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                StationElement rightNeighbor = (i < elements.size() - 1) ? elements.get(i + 1) : null;
                generatePlatform(player, world, currentLeftEdge, facing, right, length, platform, leftNeighbor, rightNeighbor);
            } else if (element instanceof BuildingElement building) {
                generateBuilding(world, currentLeftEdge, facing, building, length);
            }
            currentLeftEdge = currentLeftEdge.relative(right, element.getWidth());
        }
    }

    private static void fillTrackGap(ServerLevel world, BlockPos start, Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = BuiltInRegistries.BLOCK.get(t.ballastBlock).defaultBlockState();
        for (int l = 0; l < length; l++) {
            BlockPos P = start.relative(facing, l);
            world.setBlock(P, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(P.below(), ballast, 3);
        }
    }

    // --- 站房生成逻辑 ---
    private static void generateBuilding(ServerLevel world, BlockPos pos, Direction facing, BuildingElement element, int length) {
        Optional<StructureTemplate> custom = BuildingTemplateManager.getTemplate(element.presetName);
        StructureTemplate template = null;

        if (custom.isPresent()) {
            template = custom.get();
        } else {
            StructureTemplateManager manager = world.getStructureManager();
            ResourceLocation templateId;
            if (element.presetName.contains(":")) {
                templateId = ResourceLocation.parse(element.presetName);
            } else {
                templateId = ResourceLocation.fromNamespaceAndPath("stationbuilder", element.presetName);
            }
            template = manager.get(templateId).orElse(null);
        }

        Direction right = facing.getClockWise();

        if (template != null) {
            Rotation baseRot = getRotationFromDirection(facing);
            Rotation finalRot = baseRot.getRotated(element.rotation);

            StructurePlaceSettings data = new StructurePlaceSettings()
                    .setRotation(finalRot)
                    .setMirror(net.minecraft.world.level.block.Mirror.NONE);

            if (!element.placeAir) {
                data.addProcessor(BlockIgnoreProcessor.AIR);
            }

            // 注册强行旋转处理器（提供零 MTR 依赖的完美朝向偏转）
            data.addProcessor(new StructureProcessor() {
                @Override
                public StructureTemplate.StructureBlockInfo processBlock(
                        LevelReader world,
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

            net.minecraft.core.Vec3i size = template.getSize();
            int sx = size.getX();
            int sz = size.getZ();

            // 1. 模拟旋转，计算结构旋转后的四个角落 (在局部坐标系下)
            int[][] corners = {
                    {0, 0},
                    {sx, 0},
                    {0, sz},
                    {sx, sz}
            };

            int minX_raw = Integer.MAX_VALUE, minZ_raw = Integer.MAX_VALUE;
            int maxX_raw = Integer.MIN_VALUE, maxZ_raw = Integer.MIN_VALUE;

            for (int[] corner : corners) {
                int cx = corner[0];
                int cz = corner[1];
                int rx = cx, rz = cz;
                // 原版 StructureTemplate.transform 的旋转矩阵规律
                switch(finalRot) {
                    case CLOCKWISE_90:  rx = -cz; rz = cx;  break;
                    case CLOCKWISE_180: rx = -cx; rz = -cz; break;
                    case COUNTERCLOCKWISE_90: rx = cz; rz = -cx; break;
                    case NONE:
                    default: break;
                }
                if (rx < minX_raw) minX_raw = rx;
                if (rx > maxX_raw) maxX_raw = rx;
                if (rz < minZ_raw) minZ_raw = rz;
                if (rz > maxZ_raw) maxZ_raw = rz;
            }

            // 2. 引入极值 +1 偏移算法，精确修正负向偏转带来的 AABB 坐标偏差
            double realMinX = minX_raw + (minX_raw < 0 ? 1 : 0);
            double realMaxX = maxX_raw + (minX_raw < 0 ? 1 : 0);
            double realMinZ = minZ_raw + (minZ_raw < 0 ? 1 : 0);
            double realMaxZ = maxZ_raw + (minZ_raw < 0 ? 1 : 0);

            // 3. 找到分配给该建筑的真实目标中心点 (Level Coordinate)
            // 修正：(W - 1) / 2.0 是准确获取分配空间正中心点的公式
            double targetX = pos.getX() + 0.5 + right.getStepX() * (element.getWidth() - 1) / 2.0 + facing.getStepX() * (length - 1) / 2.0;
            double targetZ = pos.getZ() + 0.5 + right.getStepZ() * (element.getWidth() - 1) / 2.0 + facing.getStepZ() * (length - 1) / 2.0;

            // 4. 反推起始放置点：使用修正后的物理 AABB 中心
            double placeX = targetX - (realMinX + realMaxX) / 2.0;
            double placeZ = targetZ - (realMinZ + realMaxZ) / 2.0;

            BlockPos placePos = BlockPos.containing(placeX, pos.getY(), placeZ);

            // 4. 放置结构：传入 BlockPos.ZERO 作为 pivot，让游戏底层乖乖绕 (0,0,0) 旋转，我们外部在坐标上完全补偿它
            template.placeInWorld(world, placePos, BlockPos.ZERO, data, world.random, 2);
        } else {
            // == 找不到模板时的回退火柴盒 ==
            world.players().forEach(p -> p.displayClientMessage(Component.translatable("message.stationbuilder.template_not_found", element.presetName).withStyle(ChatFormatting.RED), false));

            int buildingWidth = 8; // 沿 right 方向
            int buildingDepth = 12; // 沿 facing 方向
            int buildingHeight = 6;

            int depthOffset = (length - buildingDepth) / 2;
            int widthOffset = (element.getWidth() - buildingWidth) / 2;

            // 将手工生成的火柴盒也进行居中（加上了之前遗漏的 widthOffset）
            BlockPos centeredPos = pos.relative(facing, depthOffset).relative(right, widthOffset);

            for (int w = 0; w < buildingWidth; w++) {
                for (int d = 0; d < buildingDepth; d++) {
                    for (int y = 0; y < buildingHeight; y++) {
                        BlockPos p = centeredPos.relative(right, w).relative(facing, d).above(y);

                        if (y == 0) {
                            world.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                        } else if (y == buildingHeight - 1) {
                            world.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                        } else {
                            boolean isWall = (w == 0 || w == buildingWidth - 1 || d == 0 || d == buildingDepth - 1);
                            if (isWall) {
                                boolean isWindowPos = (d == 0 || d == buildingDepth - 1) && (w >= 2 && w <= buildingWidth - 3);
                                if ((w == 0 || w == buildingWidth - 1) && (d >= 2 && d <= buildingDepth - 3)) isWindowPos = true;

                                if (isWindowPos) {
                                    world.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
                                } else {
                                    world.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                                }
                            } else {
                                world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
            BlockPos lightPos = centeredPos.relative(right, buildingWidth / 2).relative(facing, buildingDepth / 2).above(buildingHeight - 2);
            world.setBlock(lightPos, Blocks.LANTERN.defaultBlockState(), 3);
        }
    }

    private static BlockState applySmartFacing(BlockState state, Direction toTrack) {
        // 检查是否具有水平朝向属性
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, toTrack);
        }
        // 检查是否具有通用朝向属性 (针对某些特殊的 6 面朝向方块)
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, toTrack);
        }
        // 如果没有朝向属性（如混凝土），直接返回原样
        return state;
    }

    private static void generatePlatform(ServerPlayer player, ServerLevel world, BlockPos start, Direction facing,
             Direction right, int length, PlatformElement p, StationElement leftN, StationElement rightN) {
        boolean pidsFail = false, psdFail = false;
        int minW = 0;
        int maxW = p.width - 1;
        if (p.hasCanopy && p.pillarStyle == PlatformElement.PillarStyle.NONE) {
            // 无柱雨棚：如果旁边有轨道，覆盖轨道(3格)并向外延伸1格放柱子
            if (leftN instanceof TrackElement) minW = -4;
            if (rightN instanceof TrackElement) maxW = p.width + 3;
        }

        for (int l = 0; l < length; l++) {
            // 站台基础方块生成（仅在站台宽度内）
            for (int w = 0; w < p.width; w++) {
                BlockPos pos = start.relative(right, w).relative(facing, l);
                boolean isLeftEdge = (w == 0);
                boolean isRightEdge = (w == p.width - 1);

                Direction trackDirection = null;
                if (isLeftEdge && leftN instanceof TrackElement) trackDirection = right.getOpposite();
                else if (isRightEdge && rightN instanceof TrackElement) trackDirection = right;

                if (trackDirection != null) {
                    BlockState safetyState = BuiltInRegistries.BLOCK.get(p.safetyBlock).defaultBlockState();
                    safetyState = applySmartFacing(safetyState, trackDirection);
                    world.setBlock(pos, safetyState, 3);
                } else {
                    world.setBlock(pos, getRandomMixBlock(p), 3);
                }
                if (StationBuilder.isMtrLoaded() && p.hasShieldDoors) {
                    // 检查是否在起止偏移范围内
                    int mod = (l - p.doorStartOffset) % (p.doorSpacing + 2);
                    if (mod == 0 || mod == 1) {
                        var blockId = p.psdDoorId;
                        if (isLeftEdge && leftN instanceof TrackElement && mod == 0) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement && mod == 1) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right, blockId)) psdFail = true;
                        }
                    } else {
                        //Glass or End
                        var blockId = (l == 0 || l == length - 1)? p.psdEndId : p.psdGlassId;
                        if (isLeftEdge && leftN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right, blockId)) psdFail = true;
                        }
                    }
                }
            }

            // 雨棚与支柱生成（使用扩展后的范围 minW 到 maxW）
            if (p.hasCanopy) {
                boolean pillarHere = (l - p.firstPillarOffset) % (p.pillarSpacing + 1) == 0;
                for (int w = minW; w <= maxW; w++) {
                    BlockPos basePos = start.relative(right, w).relative(facing, l);
                    // 计算高度偏移
                    int halfYOffset = calculateCanopyHalfY(w, p.width, p.canopyStyle);
                    int totalHalfY = (p.canopyHeight + 1) * 2 + halfYOffset;

                    // 2. 放置顶棚方块 (如果是“仅支柱”则跳过放置方块，但支柱逻辑仍需运行)
                    if (p.canopyStyle != PlatformElement.CanopyStyle.PILLAR_ONLY) {
                        BlockState slabState = getSlabState(p.canopySlabId, totalHalfY);
                        world.setBlock(basePos.above(totalHalfY / 2), slabState, 3);
                    }

                    // 3. 生成支柱：传递 totalHalfY 以便支柱自动对齐高度
                    if (pillarHere) {
                        generatePillars(world, facing, basePos, w, p, leftN, rightN, totalHalfY,
                        p.hasLighting && l < length - 1, p.hasLighting && l > 0);
                    }
                }
                //放置PIDS
                if (p.hasPids && l > 0 && l < length - 1 && pillarHere && StationBuilder.isMtrLoaded()) {
                    var basePos = start.relative(facing, l).relative(Direction.UP, 4);
                    if (leftN instanceof TrackElement) {
                        var pos = basePos.relative(right, 1);
                        var newFacing = facing.getClockWise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight, p.pidPoleId);
                            addPidsPole(world, pos.relative(newFacing), newFacing.getOpposite(), p.canopyHeight, p.pidPoleId);
                        } else {
                            pidsFail = true;
                        }
                    }
                    if (rightN instanceof TrackElement) {
                        var pos = basePos.relative(right, p.width - 2);
                        var newFacing = facing.getCounterClockWise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight, p.pidPoleId);
                            addPidsPole(world, pos.relative(newFacing), newFacing.getOpposite(), p.canopyHeight, p.pidPoleId);
                        } else {
                            pidsFail = true;
                        }
                    }
                }
            }
        }
        if (psdFail) {
            player.sendSystemMessage(Component.translatable("gui.stationbuilder.bad_psd_msg", p.psdEndId.getPath(), p.psdGlassId.getPath(), p.psdDoorId.getPath()));
        }
        if (pidsFail) {
            player.sendSystemMessage(Component.translatable("gui.stationbuilder.bad_pid_msg", p.pidBlockId.getPath()));
        }
    }

    private static void addPidsPole(ServerLevel world, BlockPos pos, Direction facing, int maxHeight, ResourceLocation poleId) {
        int k = 1;
        while (world.getBlockState(pos.above(k)).isAir() && k <= maxHeight) {
            k++;
        }
        for (int h = 1; h < k; h++) {
            MTRIntegration.placePIDSPole(world, pos.above(h), facing, poleId);
        }
        convertTopToDoubleSlab(world, pos.above(k));
    }

    public static void convertTopToDoubleSlab(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.hasProperty(SlabBlock.TYPE)) {
            if (state.getValue(SlabBlock.TYPE) == SlabType.TOP) {
                BlockState newState = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                world.setBlock(pos, newState, 3);
            }
        }
    }

    private static int calculateCanopyHalfY(int w, int platformW, PlatformElement.CanopyStyle style) {
        // 仅支柱或平板式，高度偏移均为 0
        if (style == PlatformElement.CanopyStyle.FLAT || style == PlatformElement.CanopyStyle.PILLAR_ONLY) {
            return 0;
        }

        // 计算相对于站台中心的位置
        // 例如宽度为 9，中心是 4.0；宽度为 10，中心是 4.5
        float center = (platformW - 1) / 2.0f;
        float relW = w - center;

        // 站台边缘距离中心的距离（用于归一化计算，防止除以0）
        float maxRelW = Math.max(0.5f, center);

        return switch (style) {
            case V_SHAPE -> {
                // V字形 (Concave \/)：中心最低(0)，边缘最高(2个半砖)
                float factor = Math.abs(relW) / maxRelW;
                yield Math.round(factor * 2);
            }
            case INVERTED_V -> {
                // 倒V字形 (Convex /\)：中心最高(2个半砖)，边缘最低(0)
                float factor = 1.0f - (Math.abs(relW) / maxRelW);
                yield Math.round(factor * 2);
            }
            case SLANT_RIGHT -> {
                // 右上倾斜 (/)：左边缘(w=0)最低(0)，右边缘(w=platformW-1)最高(2个半砖)
                float denom = Math.max(1, platformW - 1);
                yield Math.round((w / denom) * 2);
            }
            case SLANT_LEFT -> {
                // 左上倾斜 (\)：左边缘(w=0)最高(2个半砖)，右边缘(w=platformW-1)最低(0)
                float denom = Math.max(1, platformW - 1);
                yield Math.round(((platformW - 1 - w) / denom) * 2);
            }
            default -> 0;
        };
    }

    private static BlockState getSlabState(ResourceLocation slabId, int halfY) {
        BlockState state = BuiltInRegistries.BLOCK.get(slabId).defaultBlockState();
        if (!state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return state; // 如果不是半砖，原样返回
        }

        // 如果 halfY 是偶数（如 10），对应整格高度 (5.0)，方块在 Y=5，属性为 BOTTOM
        // 如果 halfY 是奇数（如 11），对应高度 (5.5)，方块在 Y=5，属性为 TOP
        if (halfY % 2 == 0) {
            return state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        } else {
            return state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        }
    }

    private static void generateTrack(ServerPlayer player, ServerLevel world, BlockPos start,
              Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = BuiltInRegistries.BLOCK.get(t.ballastBlock).defaultBlockState();
        boolean hasMTR = StationBuilder.isMtrLoaded();
        RailShape shape = (facing.getAxis() == Direction.Axis.X)
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;

        BlockState railState = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
        for (int l = 0; l < length; l++) {
            BlockPos L = start.relative(facing, l);
            BlockPos M = L.relative(right);
            BlockPos R = M.relative(right);
            world.setBlock(L, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(L.below(), ballast, 3);
            if (!(t.isMtrTrack && hasMTR)) {
                world.setBlock(M, railState, 3);
            }
            world.setBlock(M.below(), ballast, 3);
            world.setBlock(R, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(R.below(), ballast, 3);

        }
        if (t.isMtrTrack && hasMTR) {
            BlockPos nodeStart = start.relative(right, 1);
            BlockPos nodeEnd = nodeStart.relative(facing, length - 1);
            var playerUuid = player.getUUID();
            TickScheduler.schedule(1, () -> {
                // 延迟一个tick，以确保其他方块onBreak能被正确执行
                MTRIntegration.placeRailNode(world, nodeStart, facing);
                MTRIntegration.placeRailNode(world, nodeEnd, facing);
                MTRIntegration.connectRailNodes(playerUuid, world, nodeStart, nodeEnd, 0);
            });
        }
    }

    private static BlockState getRandomMixBlock(PlatformElement p) {
        double total = 0;
        for (var slot : p.mixSlots) if (slot.weight > 0) total += slot.weight;
        if (total <= 0) return Blocks.SMOOTH_STONE.defaultBlockState();

        double r = Math.random() * total;
        double current = 0;
        for (var slot : p.mixSlots) {
            current += slot.weight;
            if (current >= r) return BuiltInRegistries.BLOCK.get(slot.blockId).defaultBlockState();
        }
        return Blocks.SMOOTH_STONE.defaultBlockState();
    }

    private static Rotation getRotationFromDirection(Direction facing) {
        return switch (facing) {
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case EAST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void generatePillars(ServerLevel world, Direction facing, BlockPos pos, int w,
            PlatformElement p, StationElement leftN, StationElement rightN, int totalHalfY, boolean frontLight, boolean backLight) {
        BlockState pillarState = BuiltInRegistries.BLOCK.get(p.pillarBlockId).defaultBlockState();

        // 计算支柱顶部的 Y 偏移量（相对于站台表面）
        // 逻辑：如果 totalHalfY 是 10 (5.0格, 下半砖)，支柱应到 4格处；
        //      如果 totalHalfY 是 11 (5.5格, 上半砖)，支柱应到 5格处以顶住上半砖。
        int pillarTopRelY = (totalHalfY - 1) / 2;
        boolean buildHere = false;

        switch (p.pillarStyle) {
            case SINGLE -> {
                boolean isCenter = (p.width % 2 != 0) ? (w == p.width / 2) : (w == p.width / 2 || w == p.width / 2 - 1);
                if (isCenter) {
                    // 从站台表面 (Y+1) 开始到 pillarTopRelY
                    buildPillarColumn(world, pos, 1, pillarTopRelY, pillarState);
                    buildHere = true;
                }
            }
            case DOUBLE -> {
                int pos1 = p.width / 3;
                int pos2 = p.width - 1 - (p.width / 3);
                if (w == pos1 || w == pos2) {
                    buildPillarColumn(world, pos, 1, pillarTopRelY, pillarState);
                    buildHere = true;
                }
            }
            case NONE -> {
                // 无柱雨棚：支柱在轨道外侧的地面上
                if ((w == -4 && leftN instanceof TrackElement) || (w == p.width + 3 && rightN instanceof TrackElement)) {
                    // 从地面 (站台下一格, 即 Y=0) 开始，到指定的雨棚高度
                    // 因为是从表面下一格起跳，所以起始偏移是 0，高度加上 1 格深度
                    buildPillarColumn(world, pos, 0, pillarTopRelY, pillarState);
                }
            }
        }
        if (buildHere) {
            BlockState lightState = BuiltInRegistries.BLOCK.get(p.lightBlockId).defaultBlockState();
            if (frontLight) world.setBlock(pos.above(pillarTopRelY).relative(facing), lightState, 3);
            if (backLight) world.setBlock(pos.above(pillarTopRelY).relative(facing.getOpposite()), lightState, 3);
        }
    }

    /**
     * 构建支柱列
     * @param basePos 所在的水平位置 (Y坐标为站台表面高度)
     * @param startRelY 起始 Y 偏移
     * @param endRelY 结束 Y 偏移（包含）
     */
    private static void buildPillarColumn(ServerLevel world, BlockPos basePos, int startRelY, int endRelY, BlockState state) {
        for (int y = startRelY; y <= endRelY; y++) {
            world.setBlock(basePos.above(y), state, 3);
        }
    }
}
