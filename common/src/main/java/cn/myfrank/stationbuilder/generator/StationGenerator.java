package cn.myfrank.stationbuilder.generator;

import cn.myfrank.stationbuilder.mtr.MTRIntegration;
import cn.myfrank.stationbuilder.mtr.MSDIntegration;
import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.BlockRotationUtil;
import cn.myfrank.stationbuilder.utils.TickScheduler;
import cn.myfrank.stationbuilder.manager.BuildingTemplateManager;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.elements.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StationGenerator {

    private static void setBlockIfEmpty(ServerWorld world, BlockPos pos, BlockState state) {
        if (world.getBlockState(pos).isAir() || CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
        }
    }

    /**
     * 将半砖转换为全砖（Double Slab），确保搭接点稳固
     */
    private static void ensureDoubleSlab(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
            world.setBlockState(pos, state.with(SlabBlock.TYPE, SlabType.DOUBLE), 3);
        }
    }

    public static void build(ServerPlayerEntity player, ServerWorld world, BlockPos origin, Direction facing, int length, List<StationElement> elements) {
        Direction right = facing.rotateYClockwise();

        int totalWidth = 0;
        int maxConstHeight = 10; // 预留接触网与横梁空间
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
                    BlockPos p = origin.offset(facing, l).offset(right, w).up(y);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;

                    Identifier id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
                    if (id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
                        mtrRails.add(p);
                    } else {
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
        List<BlockPos> trackLeftEdges = new ArrayList<>();
        List<Integer> trackIndices = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            StationElement element = elements.get(i);
            if (element instanceof TrackElement track) {
                StationElement leftNeighbor = (i > 0) ? elements.get(i - 1) : null;
                if (leftNeighbor instanceof TrackElement) {
                    fillTrackGap(world, currentLeftEdge, facing, length, track);
                    currentLeftEdge = currentLeftEdge.offset(right);
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
            currentLeftEdge = currentLeftEdge.offset(right, element.getWidth());
        }

        // --- 生成接触网 ---
        generateCatenaries(world, origin, facing, right, length, elements, trackLeftEdges, trackIndices);
    }

    private static void generateCatenaries(
            ServerWorld world, BlockPos origin, Direction facing, Direction right, int length,
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

            if (representativeTrack.hasCatenary && representativeTrack.catenaryState != 0) {
                buildCatenaryForGroup(
                        world, origin, facing, right, length,
                        trackCountInGroup, groupStart, groupEnd, trackLeftEdges,
                        representativeTrack, pL, pR
                );
            }

            groupStart = groupEnd + 1;
        }
    }

    /**
     * 强行沿 right 方向连续铺设横梁，确保从起点到终点完全连续无缝隙
     */
    private static void drawBeamContinuous(ServerWorld world, BlockPos startPos, Direction right, int blockCount, Identifier blockId, List<BlockPos> recordedBlocks) {
        BlockState state = CommonUtil.getBlockState(blockId);
        BlockPos cur = startPos;
        for (int i = 0; i < blockCount; i++) {
            world.setBlockState(cur, state, Block.NOTIFY_ALL);
            recordedBlocks.add(cur);
            cur = cur.offset(right);
        }
    }

    /**
     * 强行计算并写入所有墙体方块的四向连接属性，彻底消除接缝断开现象
     */
    private static void fixWallConnections(ServerWorld world, List<BlockPos> wallPositions) {
        for (BlockPos pos : wallPositions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.offset(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.NORTH_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.SOUTH_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.EAST_WALL_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.with(Properties.WEST_WALL_SHAPE, WallShape.LOW);
                }
                world.setBlockState(pos, state, 3 | 16);
            }
        }
    }

    private static void buildCatenaryForGroup(
            ServerWorld world, BlockPos origin, Direction facing, Direction right, int length,
            int trackCount, int groupStart, int groupEnd, List<BlockPos> trackLeftEdges,
            TrackElement trackCfg, PlatformElement pL, PlatformElement pR
    ) {
        int spacing = Math.max(6, trackCfg.catenarySpacing);
        int wireHeight = Math.max(4, trackCfg.catenaryHeight); // 接触线默认高度 6

        boolean useMsd = trackCfg.catenaryState == 2 && CommonUtil.isMsdLoaded();
        CatenaryTypeMapping msdType = CatenaryTypeMapping.values()[Math.min(trackCfg.catenaryModeIndex, CatenaryTypeMapping.values().length - 1)];

        // soft catenary 时横梁提高一格
        boolean isSoftCatenary = (!useMsd) || (msdType != CatenaryTypeMapping.MSDRigidCatenary);
        int trussHeight = isSoftCatenary ? (wireHeight + 2) : (wireHeight + 1);

        boolean leftHasCanopy = (pL != null && pL.hasCanopy);
        boolean rightHasCanopy = (pR != null && pR.hasCanopy);
        boolean leftIsPlatform = (pL != null);
        boolean rightIsPlatform = (pR != null);

        BlockPos firstTrackLeft = trackLeftEdges.get(groupStart);
        BlockPos lastTrackLeft = trackLeftEdges.get(groupEnd);

        BlockPos[] lastMsdNodes = new BlockPos[trackCount];
        List<BlockPos> placedWallBlocks = new ArrayList<>();

        // MSD 时，支柱往站台侧再内凹一格 (偏移格数 offsetDist: MSD 时为 2，原版时为 1)
        int leftOffsetDist = (useMsd && leftIsPlatform) ? 2 : 1;
        int rightOffsetDist = (useMsd && rightIsPlatform) ? 2 : 1;

        for (int l = 0; l < length; l++) {
            boolean isPillarFrame = (l % spacing == 0) || (l == length - 1);

            if (isPillarFrame) {
                // 计算两侧雨棚边缘处的实际高度
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

                // 轨道基准
                BlockPos baseLeft = firstTrackLeft.offset(facing, l);
                BlockPos baseRight = lastTrackLeft.offset(facing, l).offset(right, 2);

                // 支承生根位置（若内凹一格，则向站台侧延伸对应距离）
                BlockPos leftEdgePos = baseLeft.offset(right.getOpposite(), leftOffsetDist);
                BlockPos rightEdgePos = baseRight.offset(right, rightOffsetDist);

                BlockPos leftSupport = leftEdgePos.up(trussHeight);
                BlockPos rightSupport = rightEdgePos.up(trussHeight);

                boolean armFromLeft = true;

                if (trackCount == 1) {
                    // ==========================================
                    //               单线侧架 (L 架)
                    // ==========================================
                    BlockPos trackCenterTruss = baseLeft.offset(right, 1).up(trussHeight);

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
                            buildPillarDownForced(world, leftSupport.down(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                        } else {
                            armFromLeft = false;
                            drawLineForced(world, rightSupport, trackCenterTruss, trackCfg.catenaryPillarBlock, placedWallBlocks);
                            buildPillarDownForced(world, rightSupport.down(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                        }
                    }
                } else {
                    // ==========================================
                    //               多线并排 (龙门架)
                    // ==========================================
                    // 从 leftSupport 连续画到 rightSupport
                    int spanBlocks = (groupEnd - groupStart + 1) * 3 + (groupEnd - groupStart) + leftOffsetDist + rightOffsetDist;
                    drawBeamContinuous(world, leftSupport, right, spanBlocks, trackCfg.catenaryPillarBlock, placedWallBlocks);

                    // 左侧支撑
                    if (leftHasCanopy) {
                        connectToCanopy(world, leftEdgePos, trussHeight, leftCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else {
                        buildPillarDownForced(world, leftSupport.down(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                    }

                    // 右侧支撑
                    if (rightHasCanopy) {
                        connectToCanopy(world, rightEdgePos, trussHeight, rightCanopyY, trackCfg.catenaryPillarBlock, placedWallBlocks);
                    } else {
                        buildPillarDownForced(world, rightSupport.down(), trackCfg.catenaryPillarBlock, placedWallBlocks);
                    }
                }

                // --- 2. 在支架处处理接触线悬挂节点 ---
                for (int t = 0; t < trackCount; t++) {
                    BlockPos centerLine = trackLeftEdges.get(groupStart + t).offset(facing, l).offset(right, 1);
                    BlockPos catenaryPos = centerLine.up(wireHeight);

                    if (useMsd) {
                        // === MSD 接触网 ===
                        CatenaryTypeMapping type = msdType;
                        Identifier msdNodeBlock = trackCfg.catenaryBlock;

                        if (type == CatenaryTypeMapping.Auto || (msdNodeBlock.getPath().contains("catenary_with_long") && !msdNodeBlock.getPath().contains("top"))) {
                            msdNodeBlock = CommonUtil.identifierOf("msd", "catenary_with_long_top");
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
                    } else {
                        // === 原版接触网 ===
                        for (int y = wireHeight + 1; y < trussHeight; y++) {
                            world.setBlockState(centerLine.up(y), CommonUtil.getBlockState(trackCfg.catenaryPillarBlock), Block.NOTIFY_ALL);
                            placedWallBlocks.add(centerLine.up(y));
                        }
                        world.setBlockState(catenaryPos, CommonUtil.getBlockState(trackCfg.catenaryBlock), Block.NOTIFY_ALL);
                    }
                }
            }

            // --- 3. 原版接触网沿轨道铺设连续导线 ---
            if (!useMsd) {
                BlockState wireState = CommonUtil.getBlockState(trackCfg.catenaryBlock);
                for (int t = 0; t < trackCount; t++) {
                    BlockPos centerWire = trackLeftEdges.get(groupStart + t).offset(facing, l).offset(right, 1).up(wireHeight);
                    setBlockIfEmpty(world, centerWire, wireState);
                }
            }
        }

        // --- 4. 彻底消除墙体连接缝隙 ---
        fixWallConnections(world, placedWallBlocks);
    }

    /**
     * 将横梁紧密搭接在雨棚边缘上，消除一切悬空与缝隙
     */
    private static void connectToCanopy(ServerWorld world, BlockPos edgePos, int trussHeight, int canopyY, Identifier pillarBlock, List<BlockPos> recordedBlocks) {
        BlockPos canopyTopPos = edgePos.up(canopyY);
        // 将雨棚边缘处的半砖补成完整双半砖
        ensureDoubleSlab(world, canopyTopPos);
        recordedBlocks.add(canopyTopPos);

        // 用横梁/立柱方块从横梁向下垂直填补到雨棚上表面（包括两者接触面），彻底消除断缝
        BlockState pillarState = CommonUtil.getBlockState(pillarBlock);
        for (int y = canopyY + 1; y <= trussHeight; y++) {
            BlockPos fillPos = edgePos.up(y);
            world.setBlockState(fillPos, pillarState, Block.NOTIFY_ALL);
            recordedBlocks.add(fillPos);
        }
    }

    /**
     * 强行画线（横梁），确保每一格都落实并记录坐标以备后续更新连接
     */
    private static void drawLineForced(ServerWorld world, BlockPos a, BlockPos b, Identifier blockId, List<BlockPos> recordedBlocks) {
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
        world.setBlockState(p0, state, Block.NOTIFY_ALL);
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
                world.setBlockState(p, state, Block.NOTIFY_ALL);
                recordedBlocks.add(p);
            }
        }
    }

    /**
     * 强行向下建造立柱
     */
    private static void buildPillarDownForced(ServerWorld world, BlockPos topPos, Identifier pillarBlock, List<BlockPos> recordedBlocks) {
        BlockState state = CommonUtil.getBlockState(pillarBlock);
        BlockPos pos = topPos;
        while (world.isInBuildLimit(pos) && CommonUtil.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            recordedBlocks.add(pos);
            pos = pos.offset(Direction.DOWN);
        }
    }

    // （其余车站、站台、站房原有生成方法维持不变）
    private static void generateBuilding(ServerWorld world, BlockPos pos, Direction facing, BuildingElement element, int length) {
        Optional<StructureTemplate> custom = BuildingTemplateManager.getTemplate(element.presetName);
        StructureTemplate template = custom.orElse(null);

        if (template == null) {
            StructureTemplateManager manager = world.getStructureTemplateManager();
            Identifier templateId = element.presetName.contains(":") ? 
                    CommonUtil.identifierOf(element.presetName) : CommonUtil.identifierOf("stationbuilder", element.presetName);
            template = manager.getTemplate(templateId).orElse(null);
        }

        Direction right = facing.rotateYClockwise();

        if (template != null) {
            BlockRotation baseRot = getRotationFromDirection(facing);
            BlockRotation finalRot = baseRot.rotate(element.rotation);

            StructurePlacementData data = new StructurePlacementData()
                    .setRotation(finalRot)
                    .setMirror(net.minecraft.util.BlockMirror.NONE);

            if (!element.placeAir) {
                data.addProcessor(net.minecraft.structure.processor.BlockIgnoreStructureProcessor.IGNORE_AIR);
            }

            data.addProcessor(new net.minecraft.structure.processor.StructureProcessor() {
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

            net.minecraft.util.math.Vec3i size = template.getSize();
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

            double targetX = pos.getX() + 0.5 + right.getOffsetX() * (element.getWidth() - 1) / 2.0 + facing.getOffsetX() * (length - 1) / 2.0;
            double targetZ = pos.getZ() + 0.5 + right.getOffsetZ() * (element.getWidth() - 1) / 2.0 + facing.getOffsetZ() * (length - 1) / 2.0;

            double placeX = targetX - (realMinX + realMaxX) / 2.0;
            double placeZ = targetZ - (realMinZ + realMaxZ) / 2.0;

            BlockPos placePos = BlockPos.ofFloored(placeX, pos.getY(), placeZ);
            template.place(world, placePos, BlockPos.ORIGIN, data, world.random, 2);
        } else {
            world.getPlayers().forEach(p -> p.sendMessage(net.minecraft.text.Text.translatable("message.stationbuilder.template_not_found", element.presetName).formatted(net.minecraft.util.Formatting.RED), false));

            int buildingWidth = 8;
            int buildingDepth = 12;
            int buildingHeight = 6;

            int depthOffset = (length - buildingDepth) / 2;
            int widthOffset = (element.getWidth() - buildingWidth) / 2;

            BlockPos centeredPos = pos.offset(facing, depthOffset).offset(right, widthOffset);

            for (int w = 0; w < buildingWidth; w++) {
                for (int d = 0; d < buildingDepth; d++) {
                    for (int y = 0; y < buildingHeight; y++) {
                        BlockPos p = centeredPos.offset(right, w).offset(facing, d).up(y);

                        if (y == 0) {
                            world.setBlockState(p, Blocks.STONE_BRICKS.getDefaultState());
                        } else if (y == buildingHeight - 1) {
                            world.setBlockState(p, Blocks.OAK_PLANKS.getDefaultState());
                        } else {
                            boolean isWall = (w == 0 || w == buildingWidth - 1 || d == 0 || d == buildingDepth - 1);
                            if (isWall) {
                                boolean isWindowPos = (d == 0 || d == buildingDepth - 1) && (w >= 2 && w <= buildingWidth - 3);
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
            BlockPos lightPos = centeredPos.offset(right, buildingWidth / 2).offset(facing, buildingDepth / 2).up(buildingHeight - 2);
            world.setBlockState(lightPos, Blocks.LANTERN.getDefaultState());
        }
    }

    private static BlockState applySmartFacing(BlockState state, Direction toTrack) {
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.with(Properties.HORIZONTAL_FACING, toTrack);
        }
        if (state.contains(Properties.FACING)) {
            return state.with(Properties.FACING, toTrack);
        }
        return state;
    }

    private static void generatePlatform(ServerPlayerEntity player, ServerWorld world, BlockPos start, Direction facing,
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
                if (CommonUtil.isMtrLoaded() && p.hasShieldDoors) {
                    int mod = (l - p.doorStartOffset) % (p.doorSpacing + 2);
                    if (mod == 0 || mod == 1) {
                        var blockId = p.psdDoorId;
                        if (isLeftEdge && leftN instanceof TrackElement && mod == 0) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement && mod == 1) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right, blockId)) psdFail = true;
                        }
                    } else {
                        var blockId = (l == 0 || l == length - 1) ? p.psdEndId : p.psdGlassId;
                        if (isLeftEdge && leftN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right.getOpposite(), blockId)) psdFail = true;
                        } else if (isRightEdge && rightN instanceof TrackElement) {
                            if(!MTRIntegration.placePsdItem(world, pos.up(), right, blockId)) psdFail = true;
                        }
                    }
                }
            }

            if (p.hasCanopy) {
                boolean pillarHere = (l - p.firstPillarOffset) % (p.pillarSpacing + 1) == 0;
                for (int w = minW; w <= maxW; w++) {
                    BlockPos basePos = start.offset(right, w).offset(facing, l);
                    int halfYOffset = calculateCanopyHalfY(w, p.width, p.canopyStyle);
                    int totalHalfY = (p.canopyHeight + 1) * 2 + halfYOffset;

                    if (p.canopyStyle != PlatformElement.CanopyStyle.PILLAR_ONLY) {
                        BlockState slabState = getSlabState(p.canopySlabId, totalHalfY);
                        world.setBlockState(basePos.up(totalHalfY / 2), slabState);
                    }

                    if (pillarHere) {
                        generatePillars(world, facing, basePos, w, p, leftN, rightN, totalHalfY,
                        p.hasLighting && l < length - 1, p.hasLighting && l > 0);
                    }
                }
                if (p.hasPids && l > 0 && l < length - 1 && pillarHere && CommonUtil.isMtrLoaded()) {
                    var basePos = start.offset(facing, l).offset(Direction.UP, 4);
                    if (leftN instanceof TrackElement) {
                        var pos = basePos.offset(right, 1);
                        var newFacing = facing.rotateYClockwise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight, p.pidPoleId);
                            addPidsPole(world, pos.offset(newFacing), newFacing.getOpposite(), p.canopyHeight, p.pidPoleId);
                        } else {
                            pidsFail = true;
                        }
                    }
                    if (rightN instanceof TrackElement) {
                        var pos = basePos.offset(right, p.width - 2);
                        var newFacing = facing.rotateYCounterclockwise();
                        if (MTRIntegration.placePIDS(world, pos, newFacing, p.pidBlockId)) {
                            addPidsPole(world, pos, newFacing, p.canopyHeight, p.pidPoleId);
                            addPidsPole(world, pos.offset(newFacing), newFacing.getOpposite(), p.canopyHeight, p.pidPoleId);
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

    private static void addPidsPole(ServerWorld world, BlockPos pos, Direction facing, int maxHeight, Identifier poleId) {
        int k = 1;
        while (world.getBlockState(pos.up(k)).isAir() && k <= maxHeight) {
            k++;
        }
        for (int h = 1; h < k; h++) {
            MTRIntegration.placePIDSPole(world, pos.up(h), facing, poleId);
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

    private static BlockState getSlabState(Identifier slabId, int halfY) {
        BlockState state = net.minecraft.registry.Registries.BLOCK.get(slabId).getDefaultState();
        if (!state.contains(net.minecraft.state.property.Properties.SLAB_TYPE)) {
            return state;
        }

        if (halfY % 2 == 0) {
            return state.with(net.minecraft.state.property.Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.BOTTOM);
        } else {
            return state.with(net.minecraft.state.property.Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP);
        }
    }

    private static void fillTrackGap(ServerWorld world, BlockPos start, Direction facing, int length, TrackElement t) {
        BlockState ballast = CommonUtil.getBlockState(t.ballastBlock);
        for (int l = 0; l < length; l++) {
            BlockPos pos = start.offset(facing, l);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            world.setBlockState(pos.down(), ballast);
        }
    }

    private static void generateTrack(ServerPlayerEntity player, ServerWorld world, BlockPos start,
                                      Direction facing, Direction right, int length, TrackElement t) {
        BlockState ballast = CommonUtil.getBlockState(t.ballastBlock);
        boolean hasMTR = CommonUtil.isMtrLoaded();
        boolean hasCreate = CommonUtil.isCreateLoaded();
        RailShape shape = (facing.getAxis() == Direction.Axis.X)
                ? RailShape.EAST_WEST
                : RailShape.NORTH_SOUTH;

        BlockState railState = Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, shape);
        if (hasMTR && MTRIntegration.isValidRailType(t.track)) {
            railState = null;
        } else if (hasCreate && CreateIntegration.isValidRailType(t.track)) {
            railState = CreateIntegration.getTrackBlockState(facing, t.track);
        } else {
            try {
                railState = CommonUtil.getBlockState(t.track).with(Properties.RAIL_SHAPE, shape);
            } catch (Exception e) {
                player.sendMessage(Text.translatable("gui.stationbuilder.bad_rail_msg", t.track.getPath()));
            }
        }

        for (int l = 0; l < length; l++) {
            BlockPos L = start.offset(facing, l);
            BlockPos M = L.offset(right);
            BlockPos R = M.offset(right);
            world.setBlockState(L, Blocks.AIR.getDefaultState());
            world.setBlockState(L.down(), ballast);
            if (railState != null) {
                world.setBlockState(M, railState);
            }
            world.setBlockState(M.down(), ballast);
            world.setBlockState(R, Blocks.AIR.getDefaultState());
            world.setBlockState(R.down(), ballast);
        }

        if (railState == null && hasMTR) {
            BlockPos nodeStart = start.offset(right);
            BlockPos nodeEnd = nodeStart.offset(facing, length - 1);
            var playerUuid = player.getUuid();
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
            BlockState lightState = net.minecraft.registry.Registries.BLOCK.get(p.lightBlockId).getDefaultState();
            if (frontLight) world.setBlockState(pos.up(pillarTopRelY).offset(facing), lightState);
            if (backLight) world.setBlockState(pos.up(pillarTopRelY).offset(facing.getOpposite()), lightState);
        }
    }

    private static void buildPillarColumn(ServerWorld world, BlockPos basePos, int startRelY, int endRelY, BlockState state) {
        for (int y = startRelY; y <= endRelY; y++) {
            world.setBlockState(basePos.up(y), state);
        }
    }
}