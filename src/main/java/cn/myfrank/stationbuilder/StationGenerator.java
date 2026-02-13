package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StationGenerator {
    public static void build(ServerPlayerEntity player, ServerWorld world, BlockPos origin, Direction facing, int length, List<StationElement> elements) {
        Direction right = facing.rotateYClockwise();

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
                    BlockPos p = origin.offset(facing, l).offset(right, w).up(y);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;

                    Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
                    if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                        mtrRails.add(p);
                    } else {
                        // 使用 flag 2 (NOTIFY_LISTENERS) 且不包含 flag 1 (NOTIFY_NEIGHBORS) 抑制更新
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                    }
                }
            }
        }

        for (BlockPos p : mtrRails) {
            BlockState state = world.getBlockState(p);
            Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
            if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                state.getBlock().onBreak(world, p, state, player);
                world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        BlockPos currentLeftEdge = origin.offset(facing);
        for (int i = 0; i < elements.size(); i++) {
            StationElement element = elements.get(i);
            if (element instanceof TrackElement track) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                if (leftNeighbor instanceof TrackElement) {
                    currentLeftEdge = currentLeftEdge.offset(right);
                }
                generateTrack(player, world, currentLeftEdge, facing, right, length, track);
            } else if (element instanceof PlatformElement platform) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                StationElement rightNeighbor = (i < elements.size() - 1) ? elements.get(i + 1) : null;
                generatePlatform(player, world, currentLeftEdge, facing, right, length, platform, leftNeighbor, rightNeighbor);
            } else if (element instanceof BuildingElement building) {
                generateBuilding(world, currentLeftEdge, facing, building, length);
            }
            currentLeftEdge = currentLeftEdge.offset(right, element.getWidth());
        }
    }

    // --- 站房生成逻辑 ---
    private static void generateBuilding(ServerWorld world, BlockPos pos, Direction facing, BuildingElement element, int length) {
        StructureTemplateManager manager = world.getStructureTemplateManager();
        Optional<StructureTemplate> template = manager.getTemplate(new Identifier("stationbuilder", element.presetName));

        // 定义“右侧”方向，用于横向构件
        Direction right = facing.rotateYClockwise();

        if (template.isPresent()) {
            StructurePlacementData data = new StructurePlacementData()
                    .setRotation(getRotationFromDirection(facing))
                    .setMirror(BlockMirror.NONE);

            // 计算居中偏移 (沿 Facing 方向)
            int offset = (length - template.get().getSize().getZ()) / 2;
            BlockPos centeredPos = pos.offset(facing, offset);

            template.get().place(world, centeredPos, centeredPos, data, world.random, 2);
        } else {
            // --- 回退逻辑：生成相对坐标的火柴盒 (8x6x4) ---
            int buildingWidth = 8; // 沿 right 方向
            int buildingDepth = 12; // 沿 facing 方向
            int buildingHeight = 6;

            // 计算居中偏移 (沿 facing 方向)
            int depthOffset = (length - buildingDepth) / 2;
            BlockPos centeredPos = pos.offset(facing, depthOffset);

            for (int w = 0; w < buildingWidth; w++) { // 宽度偏移
                for (int d = 0; d < buildingDepth; d++) { // 深度偏移
                    for (int y = 0; y < buildingHeight; y++) { // 高度偏移

                        // 关键修复：使用相对偏移计算世界坐标
                        BlockPos p = centeredPos.offset(right, w).offset(facing, d).up(y);

                        if (y == 0) {
                            world.setBlockState(p, Blocks.STONE_BRICKS.getDefaultState());
                        } else if (y == buildingHeight - 1) {
                            world.setBlockState(p, Blocks.OAK_PLANKS.getDefaultState());
                        } else {
                            // 墙体判定：w 是左右侧墙，d 是前后墙
                            boolean isWall = (w == 0 || w == buildingWidth - 1 || d == 0 || d == buildingDepth - 1);
                            if (isWall) {
                                boolean isWindowPos = (d == 0 || d == buildingDepth - 1) && (w >= 2 && w <= buildingWidth - 3);
                                // 前后墙 (d=0, d=5) 的窗户
                                // 左右侧墙 (w=0, w=7) 的窗户
                                if ((w == 0 || w == buildingWidth - 1) && (d >= 2 && d <= buildingDepth - 3)) isWindowPos = true;

                                if (isWindowPos) {
                                    world.setBlockState(p, Blocks.GLASS.getDefaultState());
                                } else {
                                    world.setBlockState(p, Blocks.OAK_PLANKS.getDefaultState());
                                }
                            } else {
                                world.setBlockState(p, Blocks.AIR.getDefaultState());
                            }
                        }
                    }
                }
            }

            // 在内部放一个灯笼防止刷怪
            BlockPos lightPos = centeredPos.offset(right, buildingWidth / 2).offset(facing, buildingDepth / 2).up( buildingHeight - 2);
            world.setBlockState(lightPos, Blocks.LANTERN.getDefaultState());
        }
    }

    private static BlockState applySmartFacing(BlockState state, Direction toTrack) {
        // 检查是否具有水平朝向属性
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.with(Properties.HORIZONTAL_FACING, toTrack);
        }
        // 检查是否具有通用朝向属性 (针对某些特殊的 6 面朝向方块)
        if (state.contains(Properties.FACING)) {
            return state.with(Properties.FACING, toTrack);
        }
        // 如果没有朝向属性（如混凝土），直接返回原样
        return state;
    }

    private static void generatePlatform(ServerPlayerEntity player, ServerWorld world, BlockPos start, Direction facing,
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
                BlockPos pos = start.offset(right, w).offset(facing, l);
                boolean isLeftEdge = (w == 0);
                boolean isRightEdge = (w == p.width - 1);

                Direction trackDirection = null;
                if (isLeftEdge && leftN instanceof TrackElement) trackDirection = right.getOpposite();
                else if (isRightEdge && rightN instanceof TrackElement) trackDirection = right;

                if (trackDirection != null) {
                    BlockState safetyState = net.minecraft.registry.Registries.BLOCK.get(p.safetyBlock).getDefaultState();
                    safetyState = applySmartFacing(safetyState, trackDirection);
                    world.setBlockState(pos, safetyState);
                } else {
                    world.setBlockState(pos, getRandomMixBlock(p));
                }
                if (StationBuilder.isMtrLoaded() && p.hasShieldDoors) {
                    // 检查是否在起止偏移范围内
                    int mod = (l - p.doorStartOffset) % (p.doorSpacing + 2);
                    if (mod == 0 || mod == 1) {
                        var blockId = p.psdDoorId;
                        if (isLeftEdge && leftN instanceof TrackElement && mod == 0) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement && mod == 1) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right, blockId)) psdFail = true;
                        }
                    } else {
                        //Glass or End
                        var blockId = (l == 0 || l == length - 1)? p.psdEndId : p.psdGlassId;
                        if (isLeftEdge && leftN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right, blockId)) psdFail = true;
                        }
                    }
                }
            }

            // 雨棚与支柱生成（使用扩展后的范围 minW 到 maxW）
            if (p.hasCanopy) {
                boolean pillarHere = (l - p.firstPillarOffset) % (p.pillarSpacing + 1) == 0;
                for (int w = minW; w <= maxW; w++) {
                    BlockPos basePos = start.offset(right, w).offset(facing, l);
                    // 计算高度偏移
                    int halfYOffset = calculateCanopyHalfY(w, p.width, p.canopyStyle);
                    int totalHalfY = (p.canopyHeight + 1) * 2 + halfYOffset;

                    // 2. 放置顶棚方块 (如果是“仅支柱”则跳过放置方块，但支柱逻辑仍需运行)
                    if (p.canopyStyle != PlatformElement.CanopyStyle.PILLAR_ONLY) {
                        BlockState slabState = getSlabState(p.canopySlabId, totalHalfY);
                        world.setBlockState(basePos.up(totalHalfY / 2), slabState);
                    }

                    // 3. 生成支柱：传递 totalHalfY 以便支柱自动对齐高度
                    if (pillarHere) {
                        generatePillars(world, facing, basePos, w, p, leftN, rightN, totalHalfY,
                        p.hasLighting && l < length - 1, p.hasLighting && l > 0);
                    }
                }
                //放置PIDS
                if (l > 0 && l < length - 1 && pillarHere && StationBuilder.isMtrLoaded()) {
                    var basePos = start.offset(facing, l).offset(Direction.UP, 4);
                    if (leftN instanceof TrackElement) {
                        var pos = basePos.offset(right, 1);
                        var newFacing = facing.rotateYClockwise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight);
                            addPidsPole(world, pos.offset(newFacing), newFacing.getOpposite(), p.canopyHeight);
                        } else {
                            pidsFail = true;
                        }
                    }
                    if (rightN instanceof TrackElement) {
                        var pos = basePos.offset(right, p.width - 2);
                        var newFacing = facing.rotateYCounterclockwise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight);
                            addPidsPole(world, pos.offset(newFacing), newFacing.getOpposite(), p.canopyHeight);
                        } else {
                            pidsFail = true;
                        }
                    }
                }
            }
        }
        if (psdFail) {
            player.sendMessage(Text.translatable("gui.stationbuilder.bad_psd_msg", p.psdEndId.getPath(), p.psdGlassId.getPath(), p.psdDoorId.getPath()));
        }
        if (pidsFail) {
            player.sendMessage(Text.translatable("gui.stationbuilder.bad_pid_msg", p.pidBlockId.getPath()));
        }
    }

    private static void addPidsPole(ServerWorld world, BlockPos pos, Direction facing, int maxHeight) {
        int k = 1;
        while (world.getBlockState(pos.up(k)).isAir() && k <= maxHeight) {
            k++;
        }
        for (int h = 1; h < k; h++) {
            MTRIntegration.placePIDSPole(world, pos.up(h), facing);
        }
        convertTopToDoubleSlab(world, pos.up(k));
    }

    public static void convertTopToDoubleSlab(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.contains(SlabBlock.TYPE)) {
            if (state.get(SlabBlock.TYPE) == SlabType.TOP) {
                BlockState newState = state.with(SlabBlock.TYPE, SlabType.DOUBLE);
                world.setBlockState(pos, newState, 3);
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

    private static BlockState getSlabState(Identifier slabId, int halfY) {
        BlockState state = net.minecraft.registry.Registries.BLOCK.get(slabId).getDefaultState();
        if (!state.contains(net.minecraft.state.property.Properties.SLAB_TYPE)) {
            return state; // 如果不是半砖，原样返回
        }

        // 如果 halfY 是偶数（如 10），对应整格高度 (5.0)，方块在 Y=5，属性为 BOTTOM
        // 如果 halfY 是奇数（如 11），对应高度 (5.5)，方块在 Y=5，属性为 TOP
        if (halfY % 2 == 0) {
            return state.with(net.minecraft.state.property.Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.BOTTOM);
        } else {
            return state.with(net.minecraft.state.property.Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP);
        }
    }

    private static void generateTrack(ServerPlayerEntity player, ServerWorld world, BlockPos start,
              Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = net.minecraft.registry.Registries.BLOCK.get(t.ballastBlock).getDefaultState();
        boolean hasMTR = StationBuilder.isMtrLoaded();
        net.minecraft.block.enums.RailShape shape = (facing.getAxis() == Direction.Axis.X)
                ? net.minecraft.block.enums.RailShape.EAST_WEST
                : net.minecraft.block.enums.RailShape.NORTH_SOUTH;

        BlockState railState = Blocks.RAIL.getDefaultState().with(net.minecraft.state.property.Properties.RAIL_SHAPE, shape);
        for (int l = 0; l < length; l++) {
            BlockPos L = start.offset(facing, l);
            BlockPos M = L.offset(right);
            BlockPos R = M.offset(right);
            world.setBlockState(L, Blocks.AIR.getDefaultState());
            world.setBlockState(L.down(), ballast);
            if (!(t.isMtrTrack && hasMTR)) {
                world.setBlockState(M, railState);
            }
            world.setBlockState(M.down(), ballast);
            world.setBlockState(R, Blocks.AIR.getDefaultState());
            world.setBlockState(R.down(), ballast);

        }
        if (t.isMtrTrack && hasMTR) {
            BlockPos nodeStart = start.offset(right, 1);
            BlockPos nodeEnd = nodeStart.offset(facing, length - 1);
            var playerUuid = player.getUuid();
            TickScheduler.schedule(1, () -> {
                // 延迟一个tick，以确保其他方块onBreak能被正确执行
                MTRIntegration.placeRailNode(world, nodeStart, facing);
                MTRIntegration.placeRailNode(world, nodeEnd, facing);
                MTRIntegration.connectRailNodes(playerUuid, world, nodeStart, nodeEnd,0);
            });
        }
    }

    private static BlockState getRandomMixBlock(PlatformElement p) {
        double total = 0;
        for (var slot : p.mixSlots) if (slot.weight > 0) total += slot.weight;
        if (total <= 0) return Blocks.SMOOTH_STONE.getDefaultState();

        double r = Math.random() * total;
        double current = 0;
        for (var slot : p.mixSlots) {
            current += slot.weight;
            if (current >= r) return net.minecraft.registry.Registries.BLOCK.get(slot.blockId).getDefaultState();
        }
        return Blocks.SMOOTH_STONE.getDefaultState();
    }

    private static BlockRotation getRotationFromDirection(Direction facing) {
        return switch (facing) {
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            case EAST -> BlockRotation.CLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static void generatePillars(ServerWorld world, Direction facing, BlockPos pos, int w,
            PlatformElement p, StationElement leftN, StationElement rightN, int totalHalfY, boolean frontLight, boolean backLight) {
        BlockState pillarState = net.minecraft.registry.Registries.BLOCK.get(p.pillarBlockId).getDefaultState();

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
            BlockState lightState = net.minecraft.registry.Registries.BLOCK.get(p.lightBlockId).getDefaultState();
            if (frontLight) world.setBlockState(pos.up(pillarTopRelY).offset(facing), lightState);
            if (backLight) world.setBlockState(pos.up(pillarTopRelY).offset(facing.getOpposite()), lightState);
        }
    }

    /**
     * 构建支柱列
     * @param basePos 所在的水平位置 (Y坐标为站台表面高度)
     * @param startRelY 起始 Y 偏移
     * @param endRelY 结束 Y 偏移（包含）
     */
    private static void buildPillarColumn(ServerWorld world, BlockPos basePos, int startRelY, int endRelY, BlockState state) {
        for (int y = startRelY; y <= endRelY; y++) {
            world.setBlockState(basePos.up(y), state);
        }
    }
}