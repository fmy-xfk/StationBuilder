package cn.myfrank.stationbuilder.generator;

import cn.myfrank.stationbuilder.cee.CEEIntegration;
import cn.myfrank.stationbuilder.cpw.CPWAngle;
import cn.myfrank.stationbuilder.cpw.CPWIntegration;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.elements.*;
import cn.myfrank.stationbuilder.manager.BuildingTemplateManager;
import cn.myfrank.stationbuilder.mtr.MSDIntegration;
import cn.myfrank.stationbuilder.mtr.MTRIntegration;
import cn.myfrank.stationbuilder.utils.BlockRotationUtil;
import cn.myfrank.stationbuilder.utils.CatenaryType;
import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
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

    private static void setBlockIfEmpty(ServerLevel world, BlockPos pos, BlockState state) {
        if (world.getBlockState(pos).isAir() || CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, 3);
        }
    }

    private static void drawLineForced(ServerLevel world, BlockPos a, BlockPos b, ResourceLocation blockId, List<BlockPos> recordedBlocks) {
        BlockState state = CommonUtil.getBlockState(blockId);
        int x1 = a.getX(), y1 = a.getY(), z1 = a.getZ();
        int x2 = b.getX(), y2 = b.getY(), z2 = b.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x1 < x2 ? 1 : -1;
        int ys = y1 < y2 ? 1 : -1;
        int zs = z1 < z2 ? 1 : -1;

        int x = x1;
        int y = y1;
        int z = z1;

        BlockPos p0 = new BlockPos(x, y, z);
        world.setBlock(p0, state, Block.UPDATE_ALL);
        recordedBlocks.add(p0);

        int max = Math.max(dx, Math.max(dy, dz));
        if (max == 0) return;

        for (int i = 1; i <= max; i++) {
            int curX = x1 + Math.round((float) (i * (x2 - x1)) / max);
            int curY = y1 + Math.round((float) (i * (y2 - y1)) / max);
            int curZ = z1 + Math.round((float) (i * (z2 - z1)) / max);
            while (x != curX || y != curY || z != curZ) {
                if (x != curX) x += xs;
                else if (z != curZ) z += zs;
                else if (y != curY) y += ys;
                BlockPos p = new BlockPos(x, y, z);
                world.setBlock(p, state, Block.UPDATE_ALL);
                recordedBlocks.add(p);
            }
        }
    }

    private static void drawBeamContinuous(ServerLevel world, BlockPos startPos, Direction right, int blockCount, ResourceLocation blockId, List<BlockPos> recordedBlocks) {
        BlockState state = CommonUtil.getBlockState(blockId);
        BlockPos cur = startPos;
        for (int i = 0; i < blockCount; i++) {
            world.setBlock(cur, state, Block.UPDATE_ALL);
            recordedBlocks.add(cur);
            cur = cur.relative(right);
        }
    }

    private static void buildPillarDownForced(ServerLevel world, BlockPos topPos, ResourceLocation pillarBlock, List<BlockPos> recordedBlocks) {
        BlockState state = CommonUtil.getBlockState(pillarBlock);
        BlockPos pos = topPos;
        while (!world.isOutsideBuildHeight(pos) && CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, Block.UPDATE_ALL);
            recordedBlocks.add(pos);
            pos = pos.relative(Direction.DOWN);
        }
    }

    private static void ensureDoubleSlab(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)) {
            world.setBlock(pos, state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
        }
    }

    private static void fixWallConnections(ServerLevel world, List<BlockPos> wallPositions) {
        for (BlockPos pos : wallPositions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.relative(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
                }
                world.setBlock(pos, state, 3 | 16);
            }
        }
    }

    public static void build(ServerPlayer player, ServerLevel world, BlockPos origin, Direction facing, int length, List<StationElement> elements) {
        Direction right = facing.getClockWise();

        int totalWidth = 0;
        int maxConstHeight = 10;
        for (int i = 0; i < elements.size(); i++) {
            StationElement e = elements.get(i);
            StationElement eLeft = i > 0 ? elements.get(i - 1) : null;
            totalWidth += e.getWidth();
            if (eLeft instanceof TrackElement && e instanceof TrackElement) {
                totalWidth++;
            }
            if (e instanceof PlatformElement p && p.hasCanopy) {
                maxConstHeight = Math.max(maxConstHeight, p.canopyHeight + 4);
            }
            if (e instanceof TrackElement t && t.hasCatenary) {
                maxConstHeight = Math.max(maxConstHeight, t.catenaryHeight + 4);
            }
        }

        List<BlockPos> mtrRails = new ArrayList<>();
        int deltaL = elements.get(0) instanceof TrackElement ? 1 : 0;
        int deltaR = elements.get(elements.size() - 1) instanceof TrackElement ? 1 : 0;
        for (int l = 1; l <= length; l++) {
            for (int w = -deltaL; w < totalWidth + deltaR; w++) {
                for (int y = 0; y <= maxConstHeight; y++) {
                    BlockPos p = origin.relative(facing, l).relative(right, w).above(y);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;

                    ResourceLocation id = CommonUtil.getBlockId(state.getBlock());
                    if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                        mtrRails.add(p);
                    } else {
                        world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        for (BlockPos p : mtrRails) {
            BlockState state = world.getBlockState(p);
            ResourceLocation id = CommonUtil.getBlockId(state.getBlock());
            if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                state.getBlock().playerWillDestroy(world, p, state, player);
                world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        BlockPos currentLeftEdge = origin.relative(facing);
        List<BlockPos> trackLeftEdges = new ArrayList<>();
        List<Integer> trackIndices = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            StationElement element = elements.get(i);
            if (element instanceof TrackElement track) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                if (leftNeighbor instanceof TrackElement) {
                    fillTrackGap(world, currentLeftEdge, facing, right, length, track);
                    currentLeftEdge = currentLeftEdge.relative(right);
                }
                trackLeftEdges.add(currentLeftEdge);
                trackIndices.add(i);
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

        // --- 生成接触网 ---
        generateCatenaries(player, world, origin, facing, right, length, elements, trackLeftEdges, trackIndices);
    }

    private static void generateCatenaries(
            ServerPlayer player, ServerLevel world, BlockPos origin, Direction facing, Direction right, int length,
            List<StationElement> elements, List<BlockPos> trackLeftEdges, List<Integer> trackIndices
    ) {
        if (trackIndices.isEmpty()) return;

        int groupStart = 0;
        while (groupStart < trackIndices.size()) {
            int groupEnd = groupStart;
            while (groupEnd + 1 < trackIndices.size() &&
                    trackIndices.get(groupEnd + 1) == trackIndices.get(groupEnd) + 1) {
                groupEnd++;
            }

            int startElemIdx = trackIndices.get(groupStart);
            int endElemIdx = trackIndices.get(groupEnd);
            int trackCountInGroup = groupEnd - groupStart + 1;

            StationElement leftNeighbor = (startElemIdx > 0) ? elements.get(startElemIdx - 1) : null;
            StationElement rightNeighbor = (endElemIdx < elements.size() - 1) ? elements.get(endElemIdx + 1) : null;

            PlatformElement pL = (leftNeighbor instanceof PlatformElement p) ? p : null;
            PlatformElement pR = (rightNeighbor instanceof PlatformElement p) ? p : null;

            TrackElement representativeTrack = (TrackElement) elements.get(startElemIdx);

            if (representativeTrack.hasCatenary && representativeTrack.catenaryState != CatenaryType.None.ordinal()) {
                buildCatenaryForGroup(
                        player, world, origin, facing, right, length,
                        trackCountInGroup, groupStart, groupEnd, trackLeftEdges,
                        representativeTrack, pL, pR, leftNeighbor, rightNeighbor
                );
            }

            groupStart = groupEnd + 1;
        }
    }

    private static void buildCatenaryForGroup(
            ServerPlayer player, ServerLevel world, BlockPos origin, Direction facing, Direction right, int length,
            int trackCount, int groupStart, int groupEnd, List<BlockPos> trackLeftEdges,
            TrackElement trackCfg, PlatformElement pL, PlatformElement pR,
            StationElement leftNeighbor, StationElement rightNeighbor
    ) {
        int spacing = Math.max(6, trackCfg.catenarySpacing);
        int wireHeight = Math.max(4, trackCfg.catenaryHeight); // 基础接触线导高（默认 6）

        boolean useMsd = trackCfg.catenaryState == CatenaryType.MSD.ordinal() && CommonUtil.isMsdLoaded();
        boolean useCee = trackCfg.catenaryState == CatenaryType.CEE.ordinal() && CommonUtil.isCeeLoaded();
        boolean useCpw = trackCfg.catenaryState == CatenaryType.CPW.ordinal() && CommonUtil.isCpwLoaded();
        CatenaryTypeMapping msdType = CatenaryTypeMapping.values()[Math.min(trackCfg.catenaryModeIndex, CatenaryTypeMapping.values().length - 1)];

        // 确定 CPW 腕臂与线轴 ID
        ResourceLocation cpwCantileverId = CPWIntegration.DEFAULT_CANTILEVER;
        ResourceLocation cpwWireCoilId = trackCfg.catenaryBlock;
        if (trackCfg.catenaryBlock.getPath().contains("cantilever")) {
            cpwCantileverId = trackCfg.catenaryBlock;
            cpwWireCoilId = CPWIntegration.WIRE_COIL;
        }

        // CPW 采用独立的站台接触网建设模式：外侧 mast / 雨棚或站台 mast / 轨道间隙 mast。
        // 该模式不再建造龙门架或软横跨，因此直接从这里分流。
        if (useCpw) {
            buildCpwCatenaryForGroup(
                    player, world, origin, facing, right, length,
                    trackCount, groupStart, trackLeftEdges, trackCfg,
                    leftNeighbor, rightNeighbor, spacing, wireHeight,
                    cpwCantileverId, cpwWireCoilId
            );
            return;
        }

        // 横梁高度：CEE 抬高 3 格，MSD 软网抬高 2 格，硬网抬高 1 格
        int trussHeight;
        if (useCee) {
            trussHeight = wireHeight + 3;
        } else {
            boolean isSoftCatenary = (!useMsd) || (msdType != CatenaryTypeMapping.MSDRigidCatenary);
            trussHeight = isSoftCatenary ? (wireHeight + 2) : (wireHeight + 1);
        }

        boolean leftHasCanopy = (pL != null && pL.hasCanopy);
        boolean rightHasCanopy = (pR != null && pR.hasCanopy);
        boolean leftIsPlatform = (pL != null);
        boolean rightIsPlatform = (pR != null);

        BlockPos firstTrackLeft = trackLeftEdges.get(groupStart);
        BlockPos lastTrackLeft = trackLeftEdges.get(groupEnd);

        BlockPos[] lastMsdNodes = new BlockPos[trackCount];
        BlockPos[] lastCeeNodes = new BlockPos[trackCount];
        List<BlockPos> placedWallBlocks = new ArrayList<>();

        // MSD / CEE 接触网且相邻有站台时，支柱向站台侧内凹一格避让安全线
        int leftOffsetDist = ((useMsd || useCee) && leftIsPlatform) ? 2 : 1;
        int rightOffsetDist = ((useMsd || useCee) && rightIsPlatform) ? 2 : 1;

        for (int l = 0; l < length; l++) {
            boolean isPillarFrame = (l % spacing == 0) || (l == length - 1);

            if (isPillarFrame) {
                int leftCanopyY = 0;
                if (leftHasCanopy) {
                    int wIndex = Math.max(0, pL.width - leftOffsetDist);
                    int halfYOffset = calculateCanopyHalfY(wIndex, pL.width, pL.canopyStyle);
                    leftCanopyY = ((pL.canopyHeight + 1) * 2 + halfYOffset) / 2;
                }
                int rightCanopyY = 0;
                if (rightHasCanopy) {
                    int wIndex = Math.min(pR.width - 1, rightOffsetDist - 1);
                    int halfYOffset = calculateCanopyHalfY(wIndex, pR.width, pR.canopyStyle);
                    rightCanopyY = ((pR.canopyHeight + 1) * 2 + halfYOffset) / 2;
                }

                BlockPos baseLeft = firstTrackLeft.relative(facing, l);
                BlockPos baseRight = lastTrackLeft.relative(facing, l).relative(right, 2);

                BlockPos leftEdgePos = baseLeft.relative(right.getOpposite(), leftOffsetDist);
                BlockPos rightEdgePos = baseRight.relative(right, rightOffsetDist);

                BlockPos leftSupport = leftEdgePos.above(trussHeight);
                BlockPos rightSupport = rightEdgePos.above(trussHeight);

                boolean armFromLeft = true;

                // ========================================================
                // 1. 建造支柱框架（单轨 L 架 / 多轨龙门架）
                // ========================================================
                if (trackCount == 1) {
                    BlockPos trackCenterTruss = baseLeft.relative(right, 1).above(trussHeight);

                    if (leftHasCanopy && !rightIsPlatform) {
                        armFromLeft = true;
                        drawLineForced(world, leftSupport, trackCenterTruss, trackCfg.catenaryPillarBlock, placedWallBlocks);
                        connectToCanopy(world, leftEdgePos, trussHeight, leftCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else if (rightHasCanopy && !leftIsPlatform) {
                        armFromLeft = false;
                        drawLineForced(world, rightSupport, trackCenterTruss, trackCfg.catenaryPillarBlock, placedWallBlocks);
                        connectToCanopy(world, rightEdgePos, trussHeight, rightCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else if (leftHasCanopy && rightHasCanopy) {
                        armFromLeft = true;
                        drawLineForced(world, leftSupport, rightSupport, trackCfg.catenaryPillarBlock, placedWallBlocks);
                        connectToCanopy(world, leftEdgePos, trussHeight, leftCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                        connectToCanopy(world, rightEdgePos, trussHeight, rightCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else {
                        if (rightIsPlatform && !leftIsPlatform) {
                            armFromLeft = true;
                            drawLineForced(world, leftSupport, trackCenterTruss, trackCfg.catenaryPillarBlock, placedWallBlocks);
                            buildPillarDownForced(world, leftSupport.below(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                        } else {
                            armFromLeft = false;
                            drawLineForced(world, rightSupport, trackCenterTruss, trackCfg.catenaryPillarBlock, placedWallBlocks);
                            buildPillarDownForced(world, rightSupport.below(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                        }
                    }
                } else {
                    int spanBlocks = (groupEnd - groupStart + 1) * 3 + (groupEnd - groupStart) + leftOffsetDist + rightOffsetDist;
                    drawBeamContinuous(world, leftSupport, right, spanBlocks, trackCfg.catenaryPillarBlock, placedWallBlocks);

                    if (leftHasCanopy) {
                        connectToCanopy(world, leftEdgePos, trussHeight, leftCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else {
                        buildPillarDownForced(world, leftSupport.below(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                    }

                    if (rightHasCanopy) {
                        connectToCanopy(world, rightEdgePos, trussHeight, rightCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else {
                        buildPillarDownForced(world, rightSupport.below(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                    }
                }

                // ========================================================
                // 2. 在横梁/支柱处处理接触网节点与悬挂装配
                // ========================================================
                for (int t = 0; t < trackCount; t++) {
                    BlockPos centerLine = trackLeftEdges.get(groupStart + t).relative(facing, l).relative(right, 1);
                    BlockPos catenaryPos = centerLine.above(wireHeight);

                    if (useMsd) {
                        // === MSD 接触网 ===
                        CatenaryTypeMapping type = msdType;
                        ResourceLocation msdNodeBlock = trackCfg.catenaryBlock;

                        if (type == CatenaryTypeMapping.Auto || (msdNodeBlock.getPath().contains("catenary_with_long") && !msdNodeBlock.getPath().contains("top"))) {
                            msdNodeBlock = ResourceLocation.fromNamespaceAndPath("msd", "catenary_with_long_top");
                        }
                        if (type == CatenaryTypeMapping.Auto) {
                            type = CatenaryTypeMapping.MSDCatenary;
                        }

                        Direction nodeFacing;
                        if (trackCount == 1) {
                            nodeFacing = armFromLeft ? right : right.getOpposite();
                        } else {
                            nodeFacing = (t < trackCount / 2) ? right : right.getOpposite();
                        }

                        double dirAngle = (nodeFacing.getAxis() == Direction.Axis.X)
                                ? (nodeFacing == Direction.EAST ? 90.0 : -90.0)
                                : (nodeFacing == Direction.SOUTH ? 0.0 : 180.0);

                        boolean placed = MSDIntegration.placeCatenaryNode(world, catenaryPos, nodeFacing, dirAngle, msdNodeBlock);
                        if (placed) {
                            if (lastMsdNodes[t] != null) {
                                MSDIntegration.connectCatenary(world, lastMsdNodes[t], catenaryPos, type);
                            }
                            lastMsdNodes[t] = catenaryPos;
                        }
                    } else if (useCee) {
                        // === CEE 接触网 ===
                        for (int y = wireHeight + 1; y < trussHeight; y++) {
                            BlockPos fillPos = centerLine.above(y);
                            world.setBlock(fillPos, CommonUtil.getBlockState(trackCfg.catenaryPillarBlock), Block.UPDATE_ALL);
                            placedWallBlocks.add(fillPos);
                        }

                        CEEIntegration.placeCatenaryHolder(world, catenaryPos);

                        if (lastCeeNodes[t] != null) {
                            CEEIntegration.connectWire(world, player, lastCeeNodes[t], catenaryPos, trackCfg.catenaryBlock);
                        }
                        lastCeeNodes[t] = catenaryPos;
                    }
                }
            }

            // ========================================================
            // 3. 原版接触网沿轨道铺设连续导线方块（MSD/CEE 均跳过此步）
            // ========================================================
            if (!useMsd && !useCee) {
                BlockState wireState = CommonUtil.getBlockState(trackCfg.catenaryBlock);
                for (int t = 0; t < trackCount; t++) {
                    BlockPos centerWire = trackLeftEdges.get(groupStart + t).relative(facing, l).relative(right, 1).above(wireHeight);
                    setBlockIfEmpty(world, centerWire, wireState);
                }
            }
        }

        // ========================================================
        // 4. 彻底消除墙体方块缝隙
        // ========================================================
        fixWallConnections(world, placedWallBlocks);
    }

    // ========================================================
    // CPW 站台接触网：按 3 条原则布置 mast
    // 1. 最左/最右侧股道且外侧无站房、站台 -> 外侧落地 mast
    // 2. 不满足 1 但贴近站台 -> 雨棚顶 mast；没有雨棚则建在站台面上
    // 3. 其余 -> 轨道间隙 mast；间隙已有 mast 时重复利用
    // ========================================================

    private enum CpwMastBase { GROUND, PLATFORM, CANOPY }

    private static final class CpwMastPlan {
        private final int lateral;       // 相对轨道中心沿 right 方向的偏移
        private final CpwMastBase base;
        private final int baseRelY;      // 相对 origin 的 mast 底部高度（GROUND 时忽略）
        private final int minAnchorRelY; // 相对 origin 的腕臂最低安装高度（GROUND 时忽略）
        private final boolean gap;       // 是否采用轨道间隙方案
        private int resolvedGapLateral;  // 间隙方案在首榀确定后固定，保证整条导线不歪斜

        private CpwMastPlan(int lateral, CpwMastBase base, int baseRelY, int minAnchorRelY, boolean gap) {
            this.lateral = lateral;
            this.base = base;
            this.baseRelY = baseRelY;
            this.minAnchorRelY = minAnchorRelY;
            this.gap = gap;
        }
    }

    private static List<CpwMastPlan> planCpwMasts(
            int trackCount, StationElement leftNeighbor, StationElement rightNeighbor
    ) {
        List<CpwMastPlan> plans = new ArrayList<>(trackCount);
        for (int g = 0; g < trackCount; g++) {
            boolean isLeftEnd = (g == 0);
            boolean isRightEnd = (g == trackCount - 1);

            PlatformElement leftPlatform = (isLeftEnd && leftNeighbor instanceof PlatformElement p) ? p : null;
            PlatformElement rightPlatform = (isRightEnd && rightNeighbor instanceof PlatformElement p) ? p : null;
            boolean leftBlocked = isLeftEnd
                    && (leftNeighbor instanceof PlatformElement || leftNeighbor instanceof BuildingElement);
            boolean rightBlocked = isRightEnd
                    && (rightNeighbor instanceof PlatformElement || rightNeighbor instanceof BuildingElement);

            if (isLeftEnd && !leftBlocked) {
                // 原则 1：左侧外侧没有站房/站台
                plans.add(new CpwMastPlan(-3, CpwMastBase.GROUND, 0, 0, false));
            } else if (isRightEnd && !rightBlocked) {
                // 原则 1：右侧外侧没有站房/站台
                plans.add(new CpwMastPlan(3, CpwMastBase.GROUND, 0, 0, false));
            } else if (leftPlatform != null) {
                // 原则 2：贴近左侧站台
                plans.add(createPlatformMastPlan(leftPlatform, true));
            } else if (rightPlatform != null) {
                // 原则 2：贴近右侧站台
                plans.add(createPlatformMastPlan(rightPlatform, false));
            } else {
                // 原则 3：轨道间隙。优先选择右侧间隙，方便下一股道直接复用。
                boolean hasLeftGap = (g > 0);
                boolean hasRightGap = (g < trackCount - 1);
                if (hasLeftGap || hasRightGap) {
                    plans.add(new CpwMastPlan(hasRightGap ? 2 : -2, CpwMastBase.GROUND, 0, 0, true));
                } else {
                    // 单股道且两侧都被站房占满时的兜底：仍在外侧立 mast
                    plans.add(new CpwMastPlan(3, CpwMastBase.GROUND, 0, 0, false));
                }
            }
        }
        return plans;
    }

    private static CpwMastPlan createPlatformMastPlan(PlatformElement p, boolean platformOnLeft) {
        // 靠轨道一侧的平台边缘为 w=0（右侧站台）或 w=width-1（左侧站台）。
        // mast 再向平台内侧一格，避开安全线与屏蔽门；腕臂仍朝轨道伸出。
        // 宽度不足 2 的极窄站台只能让 mast 落在唯一的站台方块上。
        int mastW;
        int edgeW;
        int lateral;
        if (p.width <= 1) {
            mastW = 0;
            edgeW = 0;
            lateral = platformOnLeft ? -2 : 2;
        } else {
            mastW = platformOnLeft
                    ? Math.max(0, p.width - 2)
                    : Math.min(p.width - 1, 1);
            edgeW = platformOnLeft ? p.width - 1 : 0;
            lateral = platformOnLeft ? -3 : 3;
        }

        boolean hasCanopySlab = p.hasCanopy
                && p.canopyStyle != PlatformElement.CanopyStyle.PILLAR_ONLY;
        if (hasCanopySlab) {
            int mastCanopyTop = canopyTopRelY(p, mastW);
            int edgeCanopyTop = canopyTopRelY(p, edgeW);
            // mast 坐在雨棚顶上，腕臂必须高于雨棚边缘，避免穿模。
            int baseRelY = mastCanopyTop + 1;
            int minAnchorRelY = Math.max(mastCanopyTop, edgeCanopyTop) + 1;
            return new CpwMastPlan(lateral, CpwMastBase.CANOPY, baseRelY, minAnchorRelY, false);
        }

        // 没有雨棚（或雨棚只有立柱）时 mast 直接建在站台面上。
        return new CpwMastPlan(lateral, CpwMastBase.PLATFORM, 1, 1, false);
    }

    private static int canopyTopRelY(PlatformElement p, int w) {
        int halfYOffset = calculateCanopyHalfY(w, p.width, p.canopyStyle);
        return ((p.canopyHeight + 1) * 2 + halfYOffset) / 2;
    }

    private static void buildCpwCatenaryForGroup(
            ServerPlayer player, ServerLevel world, BlockPos origin, Direction facing, Direction right, int length,
            int trackCount, int groupStart, List<BlockPos> trackLeftEdges, TrackElement trackCfg,
            StationElement leftNeighbor, StationElement rightNeighbor,
            int spacing, int wireHeight,
            ResourceLocation cantileverId, ResourceLocation wireCoilId
    ) {
        ResourceLocation mastId = CPWIntegration.resolveMastId(trackCfg.catenaryPillarBlock);
        int nominalBaseY = origin.getY();
        // 注意：这里的 mast / 腕臂高度必须是世界绝对 Y，不能只取相对高度，
        // 否则在非 y=0 的站台上会把 mast 拉到 y=8 附近，出现几十格高的支柱。
        int cantileverY = nominalBaseY + wireHeight + 2;

        List<CpwMastPlan> plans = planCpwMasts(trackCount, leftNeighbor, rightNeighbor);
        CPWAngle mastAngle = CPWAngle.fromDirection(facing);
        BlockPos[] lastAnchors = new BlockPos[trackCount];

        for (int l = 0; l < length; l++) {
            if (l % spacing != 0 && l != length - 1) continue;

            for (int g = 0; g < trackCount; g++) {
                BlockPos center = trackLeftEdges.get(groupStart + g).relative(facing, l).relative(right, 1);
                CpwMastPlan plan = plans.get(g);

                int lateral;
                if (plan.gap) {
                    if (plan.resolvedGapLateral == 0) {
                        plan.resolvedGapLateral = chooseGapLateral(
                                world, center, right, g, trackCount, nominalBaseY
                        );
                    }
                    lateral = plan.resolvedGapLateral;
                } else {
                    lateral = plan.lateral;
                }
                BlockPos mastPos = center.relative(right, lateral);

                int baseY;
                int minAnchorY;
                if (plan.base == CpwMastBase.GROUND) {
                    // 轨道间隙位于站内，地面高度固定为 origin；不能对已有 mast 做地面扫描，
                    // 否则会把相邻股道刚建好的 mast 当成地形，导致在其顶部叠加新 mast。
                    baseY = plan.gap
                            ? nominalBaseY
                            : findGroundY(world, mastPos.getX(), mastPos.getZ(), nominalBaseY);
                    minAnchorY = baseY;
                } else {
                    baseY = nominalBaseY + plan.baseRelY;
                    minAnchorY = nominalBaseY + plan.minAnchorRelY;
                }
                int anchorY = Math.max(cantileverY, minAnchorY);
                int topY = anchorY + 1;

                // 原则 3：间隙中已有 mast 时直接复用，不再重复建造。
                boolean reuseMast = plan.gap && CPWIntegration.isMastBlock(
                        world, new BlockPos(mastPos.getX(), anchorY, mastPos.getZ())
                );
                if (!reuseMast) {
                    CPWIntegration.buildMastColumn(
                            world, mastPos.getX(), mastPos.getZ(), baseY, topY, mastAngle, mastId
                    );
                }

                // mast 在轨道左侧时腕臂向右伸，在轨道右侧时腕臂向左伸。
                CPWAngle cantileverAngle = lateral < 0
                        ? mastAngle.getClockWise()
                        : mastAngle.getCounterClockWise();
                BlockPos cantileverPos = cantileverAngle.offset(new BlockPos(mastPos.getX(), anchorY, mastPos.getZ()));
                CPWIntegration.placeCantilever(world, cantileverPos, cantileverAngle, cantileverId);

                if (lastAnchors[g] != null) {
                    CPWIntegration.connectWire(world, player, lastAnchors[g], cantileverPos, wireCoilId);
                }
                lastAnchors[g] = cantileverPos;
            }
        }
    }

    /**
     * 轨道间隙方案：先检查左右两侧间隙是否已经有 mast，有则复用；
     * 否则优先在右侧间隙新建，便于下一股道检查左侧间隙时直接复用。
     */
    private static int chooseGapLateral(
            ServerLevel world, BlockPos center, Direction right, int trackIndex, int trackCount, int nominalBaseY
    ) {
        if (trackIndex > 0) {
            BlockPos leftGap = center.relative(right, -2);
            if (hasMastInGap(world, leftGap, nominalBaseY)) return -2;
        }
        if (trackIndex < trackCount - 1) {
            BlockPos rightGap = center.relative(right, 2);
            if (hasMastInGap(world, rightGap, nominalBaseY)) return 2;
        }
        return trackIndex < trackCount - 1 ? 2 : -2;
    }

    private static boolean hasMastInGap(ServerLevel world, BlockPos gapPos, int nominalBaseY) {
        return CPWIntegration.hasMastColumn(
                world, gapPos.getX(), gapPos.getZ(), nominalBaseY - 16, nominalBaseY + 32
        );
    }

    /**
     * 外侧 mast 的落地点：从站台地面高度开始寻找第一个可放置位置，
     * 地形较高时向上让位，地形较低时向下落到实际地面。
     */
    private static int findGroundY(ServerLevel world, int x, int z, int nominalY) {
        int y = nominalY;
        int limit = nominalY + 16;
        while (y < limit && !world.isOutsideBuildHeight(y)) {
            BlockPos pos = new BlockPos(x, y, z);
            // 把旧 mast 当作可穿透结构，避免重建车站时在其顶部继续叠 mast。
            if (CommonUtil.isSoftTransparent(world.getBlockState(pos)) || CPWIntegration.isMastBlock(world, pos)) {
                break;
            }
            y++;
        }
        while (y > world.getMinBuildHeight()
                && CommonUtil.isSoftTransparent(world.getBlockState(new BlockPos(x, y - 1, z)))) {
            y--;
        }
        return y;
    }

    private static void connectToCanopy(ServerLevel world, BlockPos edgePos, int trussHeight, int canopyY, ResourceLocation pillarBlock, List<BlockPos> recordedBlocks) {
        BlockPos canopyTopPos = edgePos.above(canopyY);
        ensureDoubleSlab(world, canopyTopPos);
        recordedBlocks.add(canopyTopPos);

        BlockState pillarState = CommonUtil.getBlockState(pillarBlock);
        for (int y = canopyY + 1; y <= trussHeight; y++) {
            BlockPos fillPos = edgePos.above(y);
            world.setBlock(fillPos, pillarState, Block.UPDATE_ALL);
            recordedBlocks.add(fillPos);
        }
    }

    private static void fillTrackGap(ServerLevel world, BlockPos start, Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = CommonUtil.getBlockState(t.ballastBlock);
        for (int l = 0; l < length; l++) {
            BlockPos P = start.relative(facing, l);
            world.setBlock(P, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(P.below(), ballast, 3);
        }
    }

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

            double realMinX = minX_raw + (minX_raw < 0 ? 1 : 0);
            double realMaxX = maxX_raw + (minX_raw < 0 ? 1 : 0);
            double realMinZ = minZ_raw + (minZ_raw < 0 ? 1 : 0);
            double realMaxZ = maxZ_raw + (minZ_raw < 0 ? 1 : 0);

            double targetX = pos.getX() + 0.5 + right.getStepX() * (element.getWidth() - 1) / 2.0 + facing.getStepX() * (length - 1) / 2.0;
            double targetZ = pos.getZ() + 0.5 + right.getStepZ() * (element.getWidth() - 1) / 2.0 + facing.getStepZ() * (length - 1) / 2.0;

            double placeX = targetX - (realMinX + realMaxX) / 2.0;
            double placeZ = targetZ - (realMinZ + realMaxZ) / 2.0;

            BlockPos placePos = BlockPos.containing(placeX, pos.getY(), placeZ);
            template.placeInWorld(world, placePos, BlockPos.ZERO, data, world.random, 2);
        } else {
            world.players().forEach(p -> p.displayClientMessage(Component.translatable("message.stationbuilder.template_not_found", element.presetName).withStyle(ChatFormatting.RED), false));

            int buildingWidth = 8;
            int buildingDepth = 12;
            int buildingHeight = 6;

            int depthOffset = (length - buildingDepth) / 2;
            int widthOffset = (element.getWidth() - buildingWidth) / 2;

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
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, toTrack);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, toTrack);
        }
        return state;
    }

    private static void generatePlatform(ServerPlayer player, ServerLevel world, BlockPos start, Direction facing,
                                         Direction right, int length, PlatformElement p, StationElement leftN, StationElement rightN) {
        boolean pidsFail = false, psdFail = false;
        int minW = 0;
        int maxW = p.width - 1;
        if (p.hasCanopy && p.pillarStyle == PlatformElement.PillarStyle.NONE) {
            if (leftN instanceof TrackElement) minW = -4;
            if (rightN instanceof TrackElement) maxW = p.width + 3;
        }

        for (int l = 0; l < length; l++) {
            for (int w = 0; w < p.width; w++) {
                BlockPos pos = start.relative(right, w).relative(facing, l);
                boolean isLeftEdge = (w == 0);
                boolean isRightEdge = (w == p.width - 1);

                Direction trackDirection = null;
                if (isLeftEdge && leftN instanceof TrackElement) trackDirection = right.getOpposite();
                else if (isRightEdge && rightN instanceof TrackElement) trackDirection = right;

                if (trackDirection != null) {
                    BlockState safetyState = CommonUtil.getBlockState(p.safetyBlock);
                    safetyState = applySmartFacing(safetyState, trackDirection);
                    world.setBlock(pos, safetyState, 3);
                } else {
                    world.setBlock(pos, getRandomMixBlock(p), 3);
                }
                if (CommonUtil.isMtrLoaded() && p.hasShieldDoors) {
                    int mod = (l - p.doorStartOffset) % (p.doorSpacing + 2);
                    if (mod == 0 || mod == 1) {
                        var blockId = p.psdDoorId;
                        if (isLeftEdge && leftN instanceof TrackElement && mod == 0) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement && mod == 1) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right, blockId)) psdFail = true;
                        }
                    } else {
                        var blockId = (l == 0 || l == length - 1)? p.psdEndId : p.psdGlassId;
                        if (isLeftEdge && leftN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.above(), right, blockId)) psdFail = true;
                        }
                    }
                }
            }

            if (p.hasCanopy) {
                boolean pillarHere = (l - p.firstPillarOffset) % (p.pillarSpacing + 1) == 0;
                for (int w = minW; w <= maxW; w++) {
                    BlockPos basePos = start.relative(right, w).relative(facing, l);
                    int halfYOffset = calculateCanopyHalfY(w, p.width, p.canopyStyle);
                    int totalHalfY = (p.canopyHeight + 1) * 2 + halfYOffset;

                    if (p.canopyStyle != PlatformElement.CanopyStyle.PILLAR_ONLY) {
                        BlockState slabState = getSlabState(p.canopySlabId, totalHalfY);
                        world.setBlock(basePos.above(totalHalfY / 2), slabState, 3);
                    }

                    if (pillarHere) {
                        generatePillars(world, facing, basePos, w, p, leftN, rightN, totalHalfY,
                                p.hasLighting && l < length - 1, p.hasLighting && l > 0);
                    }
                }
                if (p.hasPids && l > 0 && l < length - 1 && pillarHere && CommonUtil.isMtrLoaded()) {
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
        if (style == PlatformElement.CanopyStyle.FLAT || style == PlatformElement.CanopyStyle.PILLAR_ONLY) {
            return 0;
        }

        float center = (platformW - 1) / 2.0f;
        float relW = w - center;
        float maxRelW = Math.max(0.5f, center);

        return switch (style) {
            case V_SHAPE -> {
                float factor = Math.abs(relW) / maxRelW;
                yield Math.round(factor * 2);
            }
            case INVERTED_V -> {
                float factor = 1.0f - (Math.abs(relW) / maxRelW);
                yield Math.round(factor * 2);
            }
            case SLANT_RIGHT -> {
                float denom = Math.max(1, platformW - 1);
                yield Math.round((w / denom) * 2);
            }
            case SLANT_LEFT -> {
                float denom = Math.max(1, platformW - 1);
                yield Math.round(((platformW - 1 - w) / denom) * 2);
            }
            default -> 0;
        };
    }

    private static BlockState getSlabState(ResourceLocation slabId, int halfY) {
        BlockState state = CommonUtil.getBlockState(slabId);
        if (!state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return state;
        }

        if (halfY % 2 == 0) {
            return state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        } else {
            return state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        }
    }

    private static void generateTrack(ServerPlayer player, ServerLevel world, BlockPos start,
                                      Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = CommonUtil.getBlockState(t.ballastBlock);
        boolean hasMTR = CommonUtil.isMtrLoaded();
        boolean hasCreate = CommonUtil.isCreateLoaded();

        RailShape shape = (facing.getAxis() == Direction.Axis.X)
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;
        BlockState railState = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape);
        if (hasMTR && MTRIntegration.isValidRailType(t.track)) {
            railState = null;
        } else if (hasCreate && CreateIntegration.isValidRailType(t.track)) {
            railState = CreateIntegration.getTrackBlockState(facing, t.track);
        } else {
            try {
                railState = CommonUtil.getBlockState(t.track).setValue(BlockStateProperties.RAIL_SHAPE, shape);
            } catch (Exception e) {
                player.sendSystemMessage(Component.translatable("gui.stationbuilder.bad_rail_msg", t.track.getPath()));
            }
        }

        for (int l = 0; l < length; l++) {
            BlockPos L = start.relative(facing, l);
            BlockPos M = L.relative(right);
            BlockPos R = M.relative(right);
            world.setBlock(L, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(L.below(), ballast, 3);
            if (railState != null) {
                world.setBlock(M, railState, 3);
            }
            world.setBlock(M.below(), ballast, 3);
            world.setBlock(R, Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(R.below(), ballast, 3);

        }
        if (railState == null && hasMTR) {
            BlockPos nodeStart = start.relative(right, 1);
            BlockPos nodeEnd = nodeStart.relative(facing, length - 1);
            var playerUuid = player.getUUID();
            TickScheduler.schedule(1, () -> {
                MTRIntegration.placeRailNode(world, nodeStart, facing);
                MTRIntegration.placeRailNode(world, nodeEnd, facing);
                MTRIntegration.connectRailNodes(playerUuid, world, nodeStart, nodeEnd, t.track);
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
            if (current >= r) return CommonUtil.getBlockState(slot.blockId);
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
        BlockState pillarState = CommonUtil.getBlockState(p.pillarBlockId);
        int pillarTopRelY = (totalHalfY - 1) / 2;
        boolean buildHere = false;

        switch (p.pillarStyle) {
            case SINGLE -> {
                boolean isCenter = (p.width % 2 != 0) ? (w == p.width / 2) : (w == p.width / 2 || w == p.width / 2 - 1);
                if (isCenter) {
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
                if ((w == -4 && leftN instanceof TrackElement) || (w == p.width + 3 && rightN instanceof TrackElement)) {
                    buildPillarColumn(world, pos, 0, pillarTopRelY, pillarState);
                }
            }
        }
        if (buildHere) {
            BlockState lightState = CommonUtil.getBlockState(p.lightBlockId);
            if (frontLight) world.setBlock(pos.above(pillarTopRelY).relative(facing), lightState, 3);
            if (backLight) world.setBlock(pos.above(pillarTopRelY).relative(facing.getOpposite()), lightState, 3);
        }
    }

    private static void buildPillarColumn(ServerLevel world, BlockPos basePos, int startRelY, int endRelY, BlockState state) {
        for (int y = startRelY; y <= endRelY; y++) {
            world.setBlock(basePos.above(y), state, 3);
        }
    }
}