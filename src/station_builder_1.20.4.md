## main\java\cn\myfrank\stationbuilder\BuildingMode.java

```java
package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildingMode {
    public enum Up {
        Clear((byte) 0),
        Tunnel((byte) 1);
        private final byte value;

        Up(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static Up fromValue(byte value) {
            return switch (value) {
                case 0 -> Clear;
                case 1 -> Tunnel;
                default -> throw new IllegalArgumentException("Invalid direction value: " + value);
            };
        }
    }

    public enum Down {
        Ballast((byte) 0),
        ThickBallast((byte) 1),
        Bridge((byte) 2);

        private final byte value;
        Down(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static Down fromValue(byte value) {
            return switch (value) {
                case 0 -> Ballast;
                case 1 -> ThickBallast;
                case 2 -> Bridge;
                default -> throw new IllegalArgumentException("Invalid direction value: " + value);
            };
        }
    }

    public static byte[] smoothModes(byte[][] modes) {
        int cols = modes[0].length;
        if (cols < 10) {
            return smoothModes(modes, 3, 0.5);
        } else if (cols < 20) {
            return smoothModes(modes, 5, 0.5);
        } else if (cols < 50) {
            return smoothModes(modes, 7, 0.5);
        } else if (cols < 100) {
            return smoothModes(modes, 11, 0.5);
        } else {
            return smoothModes(modes, 15, 0.5);
        }
    }

    public static byte[] smoothModes(byte[][] modes, int windowSize, double threshold) {
        int m = modes.length;  // 行数（样本数）
        int n = modes[0].length;  // 列数（时间/位置）

        if (m == 0 || n == 0) return null;

        // 使用更保守的默认窗口大小
        if (windowSize < 3) windowSize = 3;
        if (windowSize % 2 == 0) windowSize++;
        int halfWindow = windowSize / 2;

        byte[] result = new byte[n];

        for (int j = 0; j < n; j++) {
            // 考虑跨行和时间的二维区域
            List<Byte> candidates = new ArrayList<>();

            // 收集当前列为中心的二维窗口数据
            for (int colOffset = -halfWindow; colOffset <= halfWindow; colOffset++) {
                int actualCol = j + colOffset;
                if (actualCol < 0 || actualCol >= n) continue;

                // 对该列的所有行进行采样
                for (byte[] mode : modes) {
                    candidates.add(mode[actualCol]);
                }
            }

            // 统计频率
            Map<Byte, Integer> freqMap = new HashMap<>();
            for (byte value : candidates) {
                freqMap.put(value, freqMap.getOrDefault(value, 0) + 1);
            }

            // 找到众数
            Byte majority = null;
            int maxCount = 0;
            int totalCount = candidates.size();

            for (Map.Entry<Byte, Integer> entry : freqMap.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    majority = entry.getKey();
                }
            }

            // 检查阈值
            double majorityRatio = (double) maxCount / totalCount;
            if (majorityRatio >= threshold) {
                result[j] = majority;
            } else {
                // 如果达不到阈值，使用当前列的简单众数
                Map<Byte, Integer> colFreq = new HashMap<>();
                for (byte[] mode : modes) {
                    byte value = mode[j];
                    colFreq.put(value, colFreq.getOrDefault(value, 0) + 1);
                }

                Byte colMajority = null;
                int colMax = 0;
                for (Map.Entry<Byte, Integer> entry : colFreq.entrySet()) {
                    if (entry.getValue() > colMax) {
                        colMax = entry.getValue();
                        colMajority = entry.getKey();
                    }
                }
                if (colMajority != null) {
                    result[j] = colMajority;
                }
            }
        }
        return result;
    }
}

```

## main\java\cn\myfrank\stationbuilder\BuildingTemplateManager.java

```java
package cn.myfrank.stationbuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildingTemplateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingTemplateManager.class);
    private static final Path BUILDINGS_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("stationbuilder/buildings");
    private static final Map<String, StructureTemplate> TEMPLATES = new ConcurrentHashMap<>();

    // 启动时加载 config/stationbuilder/buildings/ 下的所有 .nbt 文件
    public static void loadTemplates() {
        TEMPLATES.clear();
        File dir = BUILDINGS_PATH.toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("Could not create buildings directory: {}", BUILDINGS_PATH);
            return;
        }

        try (var stream = Files.list(BUILDINGS_PATH)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".nbt"))
                    .forEach(p -> {
                        try {
                            String name = p.getFileName().toString().replaceFirst("\\.nbt$", "");
                            StructureTemplate template = new StructureTemplate();
                            template.readNbt(
                                    Registries.BLOCK.getReadOnlyWrapper(),
                                    NbtIo.readCompressed(p, NbtSizeTracker.ofUnlimitedBytes())
                            );
                            TEMPLATES.put(name, template);
                            LOGGER.info("Loaded building template: {}", name);
                        } catch (IOException e) {
                            LOGGER.error("Failed to load building template: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to scan buildings directory", e);
        }
    }

    public static Optional<StructureTemplate> getTemplate(String name) {
        return Optional.ofNullable(TEMPLATES.get(name));
    }

    public static List<String> getTemplateNames() {
        return new ArrayList<>(TEMPLATES.keySet());
    }

    // 运行时添加（用于导入文件）
    public static void addTemplate(String name, StructureTemplate template) {
        TEMPLATES.put(name, template);
        // 可选：同时保存到磁盘以便下次启动使用
        Path file = BUILDINGS_PATH.resolve(name + ".nbt");
        try {
            NbtCompound nbt = template.writeNbt(new NbtCompound());
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException e) {
            LOGGER.error("Failed to save template: {}", file, e);
        }
    }
}
```

## main\java\cn\myfrank\stationbuilder\CatenaryTypeMapping.java

```java
package cn.myfrank.stationbuilder;

public enum CatenaryTypeMapping {
   Auto(0),
   MSDCatenary(1),
   MSDElectric(2),
   MSDRigidCatenary(3),
   MSDRigidSoftCatenary(4);

   private final int value;

   CatenaryTypeMapping(int value) {
      this.value = value;
   }
   public CatenaryTypeMapping fromValue(int value) {
       return switch (value) {
           case 0 -> Auto;
           case 1 -> MSDCatenary;
           case 2 -> MSDElectric;
           case 3 -> MSDRigidCatenary;
           case 4 -> MSDRigidSoftCatenary;
           default -> throw new IllegalStateException("Unexpected value: " + value);
       };
   }
   public int getValue() {
      return value;
   }
   public String getName() {
      return switch(this) {
         case Auto -> "auto";
         case MSDCatenary -> "msd_catenary";
         case MSDElectric -> "msd_electric";
         case MSDRigidCatenary -> "msd_rigid_catenary";
         case MSDRigidSoftCatenary -> "msd_rigid_soft_catenary";
      };
   }
}
```

## main\java\cn\myfrank\stationbuilder\ModBlocks.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

public class ModBlocks {
    public static final StationBuilderBlock STATION_BUILDER = new StationBuilderBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK) // 拷贝铁块的基础属性
                    .requiresTool() // 必须使用对应等级的工具挖掘才会掉落
                    .strength(3.0f, 6.0f) // 设置硬度和爆炸抗性
    );
    public static final BlockItem STATION_BUILDER_ITEM = new BlockItem(STATION_BUILDER, new Item.Settings()){
        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
            tooltip.add(Text.translatable("tooltip.stationbuilder.station_builder"));
            if (stack.hasNbt() && stack.getNbt().contains("BlockEntityTag")) {
                tooltip.add(Text.translatable("gui.stationbuilder.include_config").formatted(Formatting.GOLD));
            }
        }
    };
    public static BlockEntityType<StationBuilderBlockEntity> STATION_BUILDER_ENTITY;

    public static void register() {
        Identifier id = new Identifier("stationbuilder", "station_builder");
        Registry.register(Registries.BLOCK, id, STATION_BUILDER);
        Registry.register(Registries.ITEM, id, STATION_BUILDER_ITEM);
        STATION_BUILDER_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier("stationbuilder", "station_builder_be"),
                BlockEntityType.Builder.create(StationBuilderBlockEntity::new, STATION_BUILDER).build(null)
        );
    }
}
```

## main\java\cn\myfrank\stationbuilder\ModItems.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item RAIL_BUILDER_ITEM = new RailBuilderItem(new Item.Settings().maxCount(1));

    public static void register() {
        Identifier id = new Identifier("stationbuilder", "rail_builder");
        Registry.register(Registries.ITEM, id, RAIL_BUILDER_ITEM);
    }
}

```

## main\java\cn\myfrank\stationbuilder\MSDIntegration.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mod.Init;
import top.mcmtr.core.data.Catenary;
import top.mcmtr.core.data.CatenaryType;
import top.mcmtr.core.data.OffsetPosition;
import top.mcmtr.core.data.RigidCatenary;
import top.mcmtr.mod.blocks.BlockCatenaryWithModel;
import top.mcmtr.mod.blocks.BlockNodeBase;
import top.mcmtr.mod.blocks.BlockRigidCatenaryNode;
import top.mcmtr.mod.packet.MSDPacketDeleteData;
import top.mcmtr.mod.packet.MSDPacketUpdateData;

import static top.mcmtr.mod.items.ItemRigidCatenaryConnector.getAngles;

public class MSDIntegration {
    public static boolean isCatenaryNode(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockNodeBase;
    }

    public static void clearCatenary(ServerWorld world, BlockPos pos) {
        if(MSDIntegration.isCatenaryNode(world, pos)) {
            MSDPacketDeleteData.sendDirectlyToServerCatenaryNodePosition(
                    new org.mtr.mapping.holder.ServerWorld(world),
                    Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(pos))
            );
        }
    }

    public static boolean placeCatenaryNode(ServerWorld world, BlockPos pos, Direction direction, double dirAngle, Identifier block) {
        var b = Registries.BLOCK.get(block);
        var state = new org.mtr.mapping.holder.BlockState(b.getDefaultState()).data;
        if (b instanceof BlockRigidCatenaryNode) {
            var quadrant = Angle.getQuadrant((float)dirAngle, true);
            state = state.with(BlockRigidCatenaryNode.FACING.data, quadrant % 8 >= 4)
                    .with(BlockRigidCatenaryNode.IS_45.data, quadrant % 4 >= 2)
                    .with(BlockRigidCatenaryNode.IS_22_5.data, quadrant % 2 == 1);
        } else if (b instanceof BlockCatenaryWithModel) {
            state = state.with(DirectionHelper.FACING.data, direction);
        } else {
            return false;
        }
        world.setBlockState(pos, state, 3);
        return true;
    }

    private static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, CatenaryType c) {
        BlockNodeBase.BlockNodeBaseEntity startBlockEntity = (BlockNodeBase.BlockNodeBaseEntity)world.getBlockEntity(a);
        BlockNodeBase.BlockNodeBaseEntity endBlockEntity = (BlockNodeBase.BlockNodeBaseEntity)world.getBlockEntity(b);
        if (startBlockEntity == null || endBlockEntity == null) return false;
        var a2 = new org.mtr.mapping.holder.BlockPos(a);
        var b2 = new org.mtr.mapping.holder.BlockPos(b);
        OffsetPosition offsetPositionStart = startBlockEntity.getOffsetPosition();
        OffsetPosition offsetPositionEnd = endBlockEntity.getOffsetPosition();
        Position positionStart = Init.blockPosToPosition(a2);
        Position positionEnd = Init.blockPosToPosition(b2);
        var stateStart = world.getBlockState(a);
        var stateEnd = world.getBlockState(b);
        if (c == CatenaryType.RIGID_CATENARY) {
            if (RigidCatenary.verifyPosition(positionStart, positionEnd)) {
                ObjectObjectImmutablePair<Angle, Angle> angles = getAngles(
                        a2, BlockRigidCatenaryNode.getAngle(new BlockState(stateStart)),
                        b2, BlockRigidCatenaryNode.getAngle(new BlockState(stateEnd))
                );
                RigidCatenary rigidCatenary = new RigidCatenary(
                        positionStart, angles.left(), positionEnd, angles.right(), RigidCatenary.Shape.QUADRATIC, 0.0F
                );
                world.setBlockState(a, stateStart.with(BlockNodeBase.IS_CONNECTED.data, true));
                world.setBlockState(b, stateEnd.with(BlockNodeBase.IS_CONNECTED.data, true));
                MSDPacketUpdateData.sendDirectlyToServerRigidCatenary(new org.mtr.mapping.holder.ServerWorld(world), rigidCatenary);
            }
        } else {
            if (Catenary.verifyPosition(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd)) {
                System.out.println(c);
                Catenary catenary = new Catenary(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd, c);
                world.setBlockState(a, stateStart.with(BlockNodeBase.IS_CONNECTED.data, true));
                world.setBlockState(b, stateEnd.with(BlockNodeBase.IS_CONNECTED.data, true));
                MSDPacketUpdateData.sendDirectlyToServerCatenary(new org.mtr.mapping.holder.ServerWorld(world), catenary);
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, int cType) {
        var catenaryType = switch (cType) {
            case 1 -> CatenaryType.CATENARY;
            case 2 -> CatenaryType.ELECTRIC;
            case 3 -> CatenaryType.RIGID_CATENARY;
            case 4 -> CatenaryType.RIGID_SOFT_CATENARY;
            default -> CatenaryType.NONE;
        };
        return connectCatenary(world, a, b, catenaryType);
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, CatenaryTypeMapping type) {
        // if (type == CatenaryTypeMapping.MinecraftBlock) return false;
        var catenaryType = switch (type) {
            case MSDCatenary -> CatenaryType.CATENARY;
            case MSDElectric -> CatenaryType.ELECTRIC;
            case MSDRigidCatenary -> CatenaryType.RIGID_CATENARY;
            case MSDRigidSoftCatenary -> CatenaryType.RIGID_SOFT_CATENARY;
            default -> CatenaryType.NONE;
        };
        return connectCatenary(world, a, b, catenaryType);
    }
}

```

## main\java\cn\myfrank\stationbuilder\MTRIntegration.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;

import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.*;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.packet.PacketUpdateData;
import static org.mtr.mod.block.IBlock.*;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MTRIntegration {
    private static final double EPS = 1e-6;

    public static Identifier getDefaultRailType() {
        return new Identifier("mtr", "rail_connector_160");
    }

    public static boolean isRailNode(ServerWorld world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return block == org.mtr.mod.Blocks.RAIL_NODE.get().data;
    }

    public static boolean isRailNode(BlockState state) {
        return state.getBlock() == org.mtr.mod.Blocks.RAIL_NODE.get().data;
    }
    
    public static void placeRailNode(ServerWorld world, BlockPos pos, Direction facing) {
        var mtrNodeState = org.mtr.mod.Blocks.RAIL_NODE.get().getDefaultState().data
                .with(BlockNode.FACING.data, facing == Direction.EAST || facing == Direction.WEST)
                .with(BlockNode.IS_45.data, false)
                .with(BlockNode.IS_22_5.data, false)
                .with(BlockNode.IS_CONNECTED.data, false);
        world.setBlockState(pos, mtrNodeState);
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {
        var quadrant = Angle.getQuadrant(angle, true);
        var mtrNodeState = org.mtr.mod.Blocks.RAIL_NODE.get().getDefaultState().data
                .with(BlockNode.FACING.data, quadrant % 8 >= 4)
                .with(BlockNode.IS_45.data, quadrant % 4 >= 2)
                .with(BlockNode.IS_22_5.data, quadrant % 2 == 1)
                .with(BlockNode.IS_CONNECTED.data, false);
        world.setBlockState(pos, mtrNodeState);
    }

    public static float getRailNodeAngle(ServerWorld world, BlockPos pos) {
        var state = new org.mtr.mapping.holder.BlockState(world.getBlockState(pos));
        return BlockNode.getAngle(state);
    }

    public static double getAngleFromVec3d(Vec3d v) {
        if (Math.abs(v.x) < 1e-8 && Math.abs(v.z) < 1e-8) {
            return 0.0; // 无水平方向，返回默认值
        }
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static void placePIDSPole(ServerWorld world, BlockPos pos, Direction facing, Identifier poleId) {
        var state = Registries.BLOCK.get(poleId).getDefaultState();
        if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) {
                state = state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, facing);
            } else if (state.contains(net.minecraft.state.property.Properties.FACING)) {
                state = state.with(net.minecraft.state.property.Properties.FACING, facing);
            }
        world.setBlockState(pos, state, 3);
    }

    public static boolean placePIDS(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        var block = Registries.BLOCK.get(blockId);
        if (block instanceof BlockExtension pids) {
            world.setBlockState(
                    pos,
                    pids.getDefaultState2().with(new Property<>(HorizontalFacingBlock.FACING), facing).data,
                    3
            );
            world.setBlockState(
                    pos.offset(facing),
                    pids.getDefaultState2().with(new Property<>(HorizontalFacingBlock.FACING), facing.getOpposite()).data,
                    3
            );
            world.updateNeighbors(pos, org.mtr.mapping.holder.Blocks.getAirMapped().data);
            return true;
        } else {
            return false;
        }
    }

    public static boolean placePsdItem(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        var itemRaw = Registries.ITEM.get(blockId);
        if (itemRaw instanceof ItemPSDAPGBase item) {
            ItemPSDAPGBaseAccessor accessor = (ItemPSDAPGBaseAccessor) item;
            var psdItem = accessor.item_();
            var thisItem = (EnumPSDAPGItemAccessor) (Object) psdItem;
            assert thisItem != null;
            var psdType = accessor.type_();
            var thisType = (EnumPSDAPGTypeAccessor) (Object) psdType;

            int horizontalBlocks = thisItem.isDoor() ? (thisType.isOdd() ? 3 : 2) : 1;

            for (int x = 0; x < horizontalBlocks; ++x) {
                // 计算横向偏移位置：沿站台边缘延伸
                var newPos = pos.offset(facing.rotateYClockwise(), x);

                for (int y = 0; y < 2; ++y) {
                    // 获取基础 State 并手动设置属性
                    var state = accessor.getBlockStateFromItem_()
                            .with(new Property<>(BlockPSDAPGBase.FACING.data), facing)
                            .with(new Property<>(HALF.data), y == 1 ? IBlock.DoubleBlockHalf.UPPER : IBlock.DoubleBlockHalf.LOWER);

                    if (thisItem.isDoor()) {
                        var neighborState = state
                                .with(new Property<>(SIDE.data), x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                        if (thisType.isOdd()) {
                            neighborState = neighborState.with(new Property<>(TripleHorizontalBlock.CENTER.data),
                                x > 0 && x < horizontalBlocks - 1);
                        }

                        world.setBlockState(newPos.up(y), neighborState.data);
                    } else {
                        world.setBlockState(newPos.up(y), state
                                .with(new Property<>(SIDE_EXTENDED.data), IBlock.EnumSide.SINGLE).data);
                    }
                }

                if (thisType.isPSD()) {
                    var newPos2 = new org.mtr.mapping.holder.BlockPos(newPos.up(2));
                    world.setBlockState(
                        newPos.up(2),
                        BlockPSDTop.getActualState(
                            org.mtr.mapping.holder.WorldAccess.cast(new org.mtr.mapping.holder.ServerWorld(world)),
                            newPos2
                        ).data
                    );
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static Rail connectRailNodes(
            java.util.UUID uuid, ServerWorld world, BlockPos a, BlockPos b, int speed
    ) {
        ItemRailModifier modifier;
        if (speed <= 0) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_PLATFORM.get().data;
        } else if (speed <= 20) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_20.get().data;
        } else if (speed <= 40) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_40.get().data;
        } else if (speed <= 60) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_60.get().data;
        } else if (speed <= 80) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_80.get().data;
        } else if (speed <= 100) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_100.get().data;
        } else if (speed <= 120) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_120.get().data;
        } else if (speed <= 140) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_140.get().data;
        } else if (speed <= 160) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get().data;
        } else if (speed <= 200) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_200.get().data;
        } else {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_300.get().data;
        }

        return connectRailNodes(uuid, world, a, b, modifier);
    }

    public static boolean isValidRailType(Identifier railType) {
        var itemRaw = Registries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }
    
    public static Rail connectRailNodes(
            UUID uuid, ServerWorld world, BlockPos a, BlockPos b, Identifier railType
    ) {
        var itemRaw = Registries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            return connectRailNodes(uuid, world, a, b, modifier);
        } else {
            System.out.println("Invalid rail type: " + railType);
        }
        return null;
    }

    public static Pair<Float, Float> getRailNodeAngles(
            ServerWorld world, BlockPos a, BlockPos b
    ) {
        var sa = new org.mtr.mapping.holder.BlockState(world.getBlockState(a));
        var sb = new org.mtr.mapping.holder.BlockState(world.getBlockState(b));
        if (!isRailNode(world, a) || !isRailNode(world, b)) return null;
        float facingStart = BlockNode.getAngle(sa);
        float facingEnd   = BlockNode.getAngle(sb);
        return Pair.of(facingStart, facingEnd);
    }

    public static float parseAngle(float playerYaw) {
        int quadrant = Angle.getQuadrant(playerYaw, true);
        var facing = quadrant % 8 >= 4;
        var is_45 = quadrant % 4 >= 2;
        var is_22_5 = quadrant % 2 == 1;
        return (facing ? 0 : 90) + (is_22_5 ? 22.5F : 0.0F) + (is_45 ? 45 : 0);
    }

    @NotNull
    protected static TestConnectResult testConnectRailNodes(
            float angleStart, float angleEnd, BlockPos a, BlockPos b
    ) {
        // Truncate angle
        var angles = Rail.getAngles(
                new Position(a.getX(), a.getY(), a.getZ()), parseAngle(angleStart),
                new Position(b.getX(), b.getY(), b.getZ()), parseAngle(angleEnd)
        );
        Angle facingStart = angles.left();
        Angle facingEnd   = angles.right();
        var posStart = new org.mtr.mapping.holder.BlockPos(a);
        var posEnd = new org.mtr.mapping.holder.BlockPos(b);
        var transportMode = TransportMode.TRAIN;
        ItemRailModifier modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get().data;
        var railType = ((ItemRailModifierAccessor)modifier).railType_();
        if (railType != null) {
            Position positionStart = Init.blockPosToPosition(posStart);
            Position positionEnd = Init.blockPosToPosition(posEnd);
            Rail rail;
            switch (railType) {
                case PLATFORM -> rail = Rail.newPlatformRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                case SIDING -> rail = Rail.newSidingRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                case TURN_BACK -> rail = Rail.newTurnBackRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                default -> rail = Rail.newRail(positionStart, facingStart, positionEnd, facingEnd, railType.railShape, 0.0, new ObjectArrayList<>(), railType.speedLimit, railType.speedLimit, false, false, railType.canAccelerate, railType == RailType.RUNWAY, railType.hasSignal, transportMode);
            }
            if (rail.isValid()) {
                var radii = rail.railMath.getHorizontalRadii();
                double radius;
                if (radii.leftDouble() > 0) {
                    if (radii.rightDouble() > 0) {
                        radius = Math.min(radii.leftDouble(), radii.rightDouble());
                    } else {
                        radius = radii.leftDouble();
                    }
                } else {
                    radius = radii.rightDouble();
                }
                ArrayList<Vec3d> points = new ArrayList<>();
                for(double s = 0; s <= rail.railMath.getLength(); s += 0.1) {
                    points.add(toVec3d(rail.railMath.getPosition(s, false)));
                }
                return new TestConnectResult(true, radius, rail.railMath.getLength(), points);
            }
        }
        return new TestConnectResult(false, 0, 0, new ArrayList<>());
    }

    @Nullable
    protected static Rail connectRailNodes(
            java.util.UUID uuid, ServerWorld world, BlockPos a, BlockPos b,
            ItemRailModifier modifier
    ) {
        var sa = new org.mtr.mapping.holder.BlockState(world.getBlockState(a));
        var sb = new org.mtr.mapping.holder.BlockState(world.getBlockState(b));
        if (!isRailNode(world, a) || !isRailNode(world, b)) {
            return null;
        }
        var angles = Rail.getAngles(
            new Position(a.getX(), a.getY(), a.getZ()), BlockNode.getAngle(sa),
            new Position(b.getX(), b.getY(), b.getZ()), BlockNode.getAngle(sb)
        );
        Angle facingStart = angles.left();
        Angle facingEnd   = angles.right();

        Rail rail = modifier.createRail(uuid, TransportMode.TRAIN, sa, sb,
                new org.mtr.mapping.holder.BlockPos(a), new org.mtr.mapping.holder.BlockPos(b),
                facingStart, facingEnd);

        if (rail != null) {
            world.setBlockState(a, sa.data.with(BlockNode.IS_CONNECTED.data, true), 3);
            world.setBlockState(b, sb.data.with(BlockNode.IS_CONNECTED.data, true), 3);
            PacketUpdateData.sendDirectlyToServerRail(new org.mtr.mapping.holder.ServerWorld(world), rail);
            return rail;
        } else {
            System.out.println("Failed to create rail between " + a + "(" + facingStart + ") and " + b + "(" + facingEnd + ") with modifier " + modifier);
        }
        return null;
    }

    private static void clearBlock(ServerWorld world, BlockPos pos, boolean includeCatenary) {
        if(StationBuilder.isMsdLoaded()) {
            if (MSDIntegration.isCatenaryNode(world, pos)) {
                if (includeCatenary) {
                    MSDIntegration.clearCatenary(world, pos);
                } else {
                    return;
                }
            }
        }
        world.setBlockState(pos, org.mtr.mapping.holder.Blocks.getAirMapped().data.getDefaultState());
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static Vec3d toVec3d(Vector v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    private static Vector toVector(BlockPos v) {
        return new Vector(v.getX(), v.getY(), v.getZ());
    }

    public static Direction horizontalDirectionFromVec(Vec3d v) {
        double x = v.x, z = v.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static BlockPos addCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isLeftest, boolean isRightest,
            @Nullable BlockPos lastCatenaryNode, Identifier block, CatenaryTypeMapping type, int height
    ) {
        Direction dir = horizontalDirectionFromVec(tangent);
        double dirAngle = getAngleFromVec3d(tangent);
        int blockY = (int) Math.floor(center.getY());
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));
        if (isLeftest || isRightest) {
            if (isLeftest) {
                dir = dir.rotateYClockwise();
            } else {
                dir = dir.rotateYCounterclockwise();
            }
            if (!MSDIntegration.placeCatenaryNode(world, catenaryPos, dir, dirAngle, block)){
                return null;
            }
            if (lastCatenaryNode != null) {
                MSDIntegration.connectCatenary(world, lastCatenaryNode, catenaryPos, type);
            }
            return catenaryPos;
        }
        return null;
    }

    private static void drawLine(ServerWorld world, BlockPos a, BlockPos b, Identifier lineBlock) {
        var state = Registries.BLOCK.get(lineBlock).getDefaultState();
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

        setBlockIfEmpty(world, new BlockPos(x, y, z), state);

        int max = Math.max(dx, Math.max(dy, dz));
        if (max == 0) return;

        for (int i = 1; i <= max; i++) {
            int curX = x1 + Math.round((float)(i * (x2 - x1)) / max);
            int curY = y1 + Math.round((float)(i * (y2 - y1)) / max);
            int curZ = z1 + Math.round((float)(i * (z2 - z1)) / max);
                        // 强制采取 6向（曼哈顿）步进移动，杜绝对角线产生不连接的孤立围栏
            while(x != curX || y != curY || z != curZ) {
                if (x != curX) x += xs;
                else if (z != curZ) z += zs;
                else if (y != curY) y += ys;
                setBlockIfEmpty(world, new BlockPos(x, y, z), state);
            }
        }
    }
    private static void buildPillarDown(ServerWorld world, BlockPos topPos, Identifier pillarBlock) {
        var state = Registries.BLOCK.get(pillarBlock).getDefaultState();
        BlockPos pos = topPos;
        while(world.isInBuildLimit(pos) && StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
            pos = pos.offset(Direction.DOWN);
        }
    }

    private static void setBlockIfEmpty(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
        if (world.getBlockState(pos).isAir() || StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
        }
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, Identifier pillarBlock, Identifier lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.up();
            Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * 3.0),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * 3.0)
                );
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
            } else {
                // 多条轨道时，跨越所有轨道总宽画一道拱门
                double leftSpan = -((trackCount - 1) * railSpacing + 3.0) + EPS;
                double rightSpan = 3.0 - EPS;

                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * rightSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * rightSpan)
                );
                BlockPos leftEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * leftSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * leftSpan)
                );

                drawLine(world, rightEnd, leftEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
                buildPillarDown(world, leftEnd.down(), pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
    }

    private static void clearHeights(Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
             boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        if (config.clearFullHeight) {
            var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
            int height = 0;
            for (var xz : XZs) {
                BlockPos topPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING,
                        new BlockPos(xz.x(), baseY, xz.z()));
                height = Math.max(height, topPos.getY() - baseY);
            }
            double k = Math.max(1.0, (double) height / config.tunnelHeight);
            height = Math.max(height, config.tunnelHeight);
            for (int y = 0; y <= height; ++y) {
                Vec3d layerCenter = center.add(0, y, 0);
                double halfWidth2 = halfWidth + y / k;
                int blockY = (int) Math.floor(layerCenter.y);
                var blockXZs = RailMath.getPositions(center, normal,
                        isLeftest ? halfWidth2 : halfWidth,
                        isRightest ? halfWidth2 : halfWidth);
                for (var block : blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
                }
            }
        } else {
            double clearHalfWidth = config.tunnelWidth / 2.0 + EPS;
            for (int y = 0; y <= config.tunnelHeight; ++y) {
                Vec3d layerCenter = center.add(0, y, 0);
                int blockY = (int) Math.floor(layerCenter.y);
                var blockXZs = RailMath.getPositions(center, normal,
                        isLeftest ? clearHalfWidth : halfWidth,
                        isRightest ? clearHalfWidth : halfWidth);
                for(var block: blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
                }
            }
        }
    }

    private static void buildThickerBallast(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
            boolean isLeftest, boolean isRightest
    ) {
        final int ballastHeight = config.ballastMaxThickness;
        double halfTopWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfBottomWidth = config.ballastBottomWidth / 2.0 + EPS;
        for (int y = 0; y < ballastHeight; y++) {
            double halfWidth = lerp(halfTopWidth, halfBottomWidth, (double) y / ballastHeight);
            Vec3d layerCenter = center.add(0, -y - 1, 0);
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest? halfWidth: halfTopWidth,
                    isRightest? halfWidth: halfTopWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
            }
        }
    }

    private static void buildUpDown(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config, boolean clearCatenary,
            BuildingMode.Up ubm, BuildingMode.Down dbm, boolean isLeftest, boolean isRightest, boolean pillar
    ) {
        double EPS = 1e-6;
        double halfBallastWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;
        double halfPillarWidth = 1.5;
        int blockY = (int) Math.floor(center.y);
        ArrayList<BlockPos> overpass_walls = new ArrayList<>();
        if (dbm == BuildingMode.Down.Bridge) {
            var bridgeXZs = RailMath.getPositions(center, normal, halfBridgeWidth);
            for (int i = 0; i < bridgeXZs.size(); i++) {
                var block = bridgeXZs.get(i);
                int x = block.x(), z = block.z();
                var bridgeBlockState = Registries.BLOCK.get(config.bridgeBlock).getDefaultState();
                if ((isLeftest && i == 0) || (isRightest && i == bridgeXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!isRailNode(world, pos)) {
                        world.setBlockState(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlockState(posU, Registries.BLOCK.get(config.bridgeGuardRailBlock).getDefaultState(), 3);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlockState(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        clearBlock(world, posU, clearCatenary);
                    }
                }
                if (pillar) {
                    Vec3d p = new Vec3d(x + 0.5, blockY + 0.5, z + 0.5);
                    double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
                    if (dist <= halfPillarWidth) {
                        var pos = new BlockPos(x, blockY - 2, z);
                        int solidCount = 0;
                        while(solidCount < 3 && world.isInBuildLimit(pos)) {
                            if (StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
                                world.setBlockState(pos, Registries.BLOCK.get(config.bridgePillarBlock).getDefaultState(), 3);
                                solidCount = 0;
                            } else {
                                solidCount++;
                            }
                            pos = pos.offset(Direction.DOWN);
                        }
                    }
                }
            }
        }

        if (ubm == BuildingMode.Up.Tunnel) {
            var tunnelXZs = RailMath.getPositions(center, normal, halfTunnelWidth);
            for (int i = 0; i < tunnelXZs.size(); i++) {
                var block = tunnelXZs.get(i);
                int x = block.x(), z = block.z();
                var posTop = new BlockPos(x, blockY + config.tunnelHeight, z);
                if (!isRailNode(world, posTop)) {
                    world.setBlockState(posTop, Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlockState(posBottom, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlockState(new BlockPos(x, blockY + y, z),
                                Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                    }
                } else {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        clearBlock(world, new BlockPos(x, blockY + y, z), clearCatenary);
                    }
                }
            }
        }
        if (ubm != BuildingMode.Up.Tunnel && dbm == BuildingMode.Down.Ballast) {
            var ballastXZs = RailMath.getPositions(center, normal, halfBallastWidth);
            for (var block : ballastXZs) {
                int x = block.x(), z = block.z();
                var pos = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, pos)) {
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState());
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.offset(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.NORTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.SOUTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.EAST_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.WEST_SHAPE, WallShape.LOW);
                }
                world.setBlockState(pos, state, 3 | 16);
            }
        }
    }
    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vector a, Vector b, Vector r) {
        Vector AB = new Vector(b.x - a.x, b.y - a.y, b.z - a.z);
        Vector AR = new Vector(r.x - a.x, r.y - a.y, r.z - a.z);
        return AB.x * AR.z - AB.z * AR.x;
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            UUID uuid, ServerWorld world, RailBuilderConfig config) {
        int count = startPositions.size();
        assert endPositions.size() == count;

        // Connect rails
        Rail[] rails = new Rail[count];
        double maxLength = -1.0;
        for (int i = 0; i < count; i++) {
            var rail = connectRailNodes(uuid, world, startPositions.get(i), endPositions.get(i), config.railType);
            rails[i] = rail;
            if (rail != null) {
                maxLength = Math.max(maxLength, rail.railMath.getLength());
            }
        }

        if (maxLength < 0) return false; // No rail created.
        int segments = (int) Math.floor(maxLength / 0.5);

        // First round: building modes detection
        byte[][] ubm2 = new byte[count][segments + 1];
        byte[][] dbm2 = new byte[count][segments + 1];
        boolean[] reverse = new boolean[count];
        boolean[] pillar = new boolean[segments + 1];
        pillar[0] = pillar[segments] = true;

        int pillarSegCount = (int) Math.ceil(maxLength / config.bridgeClearSpan);
        double pillarSpacing = segments / (double) pillarSegCount;
        for (int i = 0; i < pillarSegCount; i++) {
            pillar[(int)Math.floor(i * pillarSpacing)] = true;
        }

        int catenarySegCount = (int) Math.ceil(maxLength / config.catenarySpacing);
        double catenarySpacing = segments / (double) catenarySegCount;

        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;

            var pa = toVector(startPositions.get(i));
            var pb = toVector(endPositions.get(i));
            var p1 = rail.railMath.getPosition(0, false);
            reverse[i] = p1.distanceTo(pa) > p1.distanceTo(pb);
            MTRIntegration.calcBuildingMode(rail, world, config, reverse[i], segments, ubm2[i], dbm2[i]);
        }
        var ubm = BuildingMode.smoothModes(ubm2);
        var dbm = BuildingMode.smoothModes(dbm2);

        // Second round: Build tunnel, bridge, pillar, thick ballast, catenary

        // Create PointProviders, and check left and right side
        MTRPointProvider[] pps = new MTRPointProvider[count];
        boolean[] isLeftest = new boolean[count], isRightest = new boolean[count];
        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;
            pps[i] = new MTRPointProvider(rail.railMath, segments, reverse[i]);
            if (count == 1) {
                isLeftest[i] = true; isRightest[i] = true;
            } else { // count > 1
                if (i == 0 || i == count - 1) {
                    var a = startPositions.get(i);
                    var b = endPositions.get(i);
                    BlockPos r = null;
                    if (i == 0) {
                        if (rails[i + 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i + 1);
                        }
                    } else { // i == count - 1
                        if (rails[i - 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i - 1);
                        }
                    }
                    if (r != null) {
                        isRightest[i] = getSide(toVector(a), toVector(b), toVector(r)) < 0;
                        isLeftest[i] = !isRightest[i];
                    }
                }
            }
        }

        // First pass: Clear heights according to building modes
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                if (thisUbm == BuildingMode.Up.Clear) {
                    clearHeights(center, normal, world, config, isLeftest[i], isRightest[i], j > 1);
                }
            }
        }

        // Second pass: Build structures
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var thisDbm = BuildingMode.Down.fromValue(dbm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                buildUpDown(center, normal, world, config, j > 1,
                        thisUbm, thisDbm, isLeftest[i], isRightest[i], pillar[j]);
                if (thisDbm == BuildingMode.Down.ThickBallast) {
                    buildThickerBallast(center, normal, world, config, isLeftest[i], isRightest[i]);
                }
            }
        }

        // Build catenary
        boolean failToPlaceCatenaryNode = false;
        for(int i = 0; i < count; i++) {
            BlockPos lastCatenaryNode = null;
            BuildingMode.Up lastUbm = null;
            var rail = rails[i];
            if (rail == null) continue;
            MTRPointProvider pp = new MTRPointProvider(rail.railMath, segments, reverse[i]);
            for (int k = 0; k <= catenarySegCount; k++) {
                int j = k < catenarySegCount ? (int)Math.floor(k * catenarySpacing) : segments;
                var tuple = pp.get(j);
                var center = tuple.get(0);
                var tangent = tuple.get(1);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                if (config.useCatenary) {
                    if (config.isVanillaCatenary) {
                        lastCatenaryNode = addVanillaCatenaryNode(world, center, tangent, isRightest[i], count, config.railSpacing,
                                lastCatenaryNode, config.tunnelHeight - 1,
                                thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar,
                                config.catenaryBlock);
                    } else {
                        if (isLeftest[i] || isRightest[i]) {
                            CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.catenaryModeIndex];
                            Identifier pillarBlock = thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar;

                            if (type == CatenaryTypeMapping.Auto) {
                                if (thisUbm == BuildingMode.Up.Tunnel) {
                                    pillarBlock = new Identifier("msd", "rigid_catenary_node");
                                } else {
                                    pillarBlock = new Identifier("msd", "catenary_with_long");
                                }

                                if (lastUbm != null) {
                                    if (lastUbm == BuildingMode.Up.Tunnel && thisUbm == BuildingMode.Up.Tunnel) {
                                        type = CatenaryTypeMapping.MSDRigidCatenary;
                                    } else if (lastUbm == BuildingMode.Up.Clear && thisUbm == BuildingMode.Up.Clear) {
                                        type = CatenaryTypeMapping.MSDCatenary;
                                    } else {
                                        type = CatenaryTypeMapping.MSDRigidSoftCatenary;
                                    }
                                }
                            }

                            lastCatenaryNode = addCatenaryNode(world, center, tangent, isLeftest[i], isRightest[i],
                                    lastCatenaryNode, pillarBlock, type, config.tunnelHeight - 1);
                            if (lastCatenaryNode == null) {
                                failToPlaceCatenaryNode = true;
                            }
                            lastUbm = thisUbm;
                        }
                    }
                }
            }
        }
        return failToPlaceCatenaryNode;
    }

    public static void calcBuildingMode(
            Rail rail, ServerWorld world, RailBuilderConfig config, boolean reverseMath, int segments,
            byte[] upBuildingModes, byte[] downBuildingModes
    ) {
        final int thickBallastHeight = config.ballastMaxThickness;
        final double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        final double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;
        var math = rail.railMath;

        // Determine building modes for upper and lower attachments
        int i = 0;
        for (var pp = new MTRPointProvider(math, segments, reverseMath); pp.notExhausted(); pp.next(), i++) {
            var tuple = pp.get();
            Vec3d center = tuple.get(0);
            Vec3d normal = tuple.get(2);

            // Stretch left and right from the center point, and get a bundle of BlockPos
            int blockY = (int) Math.floor(center.y);
            var blockXZs = RailMath.getPositions(center, normal, Math.max(halfTunnelWidth, halfBridgeWidth));

            // Determine building modes
            BuildingMode.Up ubm = BuildingMode.Up.Clear;
            BuildingMode.Down dbm = BuildingMode.Down.Ballast;
            int roofBlocks = 0, roofSolidBlocks = 0;
            int lowBlocks = 0, lowSolidBlocks = 0;

            for (var block: blockXZs) {
                // Calculate distance to center
                int floorBlocks = 0, floorSolidBlocks = 0;
                for (int y = -thickBallastHeight - 1; y <= config.tunnelHeight + 3; y++) {
                    var pos = new BlockPos(block.x(), blockY + y, block.z());
                    if (!world.isInBuildLimit(pos)) continue;
                    var state = world.getBlockState(pos);
                    boolean isRailNode = isRailNode(world, pos);
                    boolean isSoftTransparent = StationBuilder.isSoftTransparent(state);
                    if (y < -1) {
                        // 检查是否需要更厚的路基
                        if (y == -thickBallastHeight - 1) {
                            lowBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) lowSolidBlocks += 1;
                        } else {
                            floorBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) floorSolidBlocks += 1;
                        }
                    } else if (y >= config.tunnelHeight) {
                        roofBlocks++;
                        if (!state.isAir() && !isRailNode && !StationBuilder.isNotLiquidTransparent(state)) {
                            roofSolidBlocks++;
                        }
                    }
                }
                if ((double) floorSolidBlocks / floorBlocks <= 0.7) {
                    dbm = BuildingMode.Down.ThickBallast;
                }
            }
            if ((double) roofSolidBlocks / roofBlocks >= 0.6) {
                ubm = BuildingMode.Up.Tunnel;
            }
            if ((double) lowSolidBlocks / lowBlocks <= 0.6) {
                dbm = BuildingMode.Down.Bridge;
            }
            upBuildingModes[i] = ubm.getValue();
            downBuildingModes[i] = dbm.getValue();
        }
    }
}

```

## main\java\cn\myfrank\stationbuilder\MTRPointProvider.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.util.math.Vec3d;
import org.mtr.core.tool.Vector;

import java.util.List;

public class MTRPointProvider {
    private static Vec3d toVec3d(Vector v) {
        return new Vec3d(v.x, v.y, v.z);
    }
    private static final double EPS = 0.001;
    private final org.mtr.core.data.RailMath math;
    private final int segment;
    private final double step;
    private final boolean reversed;
    private int i;
    public MTRPointProvider(org.mtr.core.data.RailMath math, int segment, boolean reversed) {
        this.math = math;
        this.segment = segment;
        this.step = math.getLength() / segment;
        this.reversed = reversed;
        if (reversed) {
            i = segment;
        } else {
            i = 0;
        }
    }
    public boolean notExhausted() {
        if (reversed) {
            return i >= 0;
        } else {
            return i <= segment;
        }
    }
    public void next() {
        if (reversed) {
            if (notExhausted()) i--;
        } else {
            if (notExhausted()) i++;
        }
    }
    private List<Vec3d> _get(int i) {
        final double length = math.getLength();
        double s = i * step;
        if (s > length) s = length;
        Vec3d center = toVec3d(math.getPosition(s, false));
        Vec3d pNext, tangent;
        if (reversed) {
            if (s - EPS < 0) {
                pNext = toVec3d(math.getPosition(Math.max(s + EPS, 0), false));
                tangent = center.subtract(pNext).normalize();
            } else {
                pNext = toVec3d(math.getPosition(Math.max(s - EPS, 0), false));
                tangent = pNext.subtract(center).normalize();
            }
        } else {
            if (s + EPS > length) {
                pNext = toVec3d(math.getPosition(Math.min(s - EPS, length), false));
                tangent = center.subtract(pNext).normalize();
            } else {
                pNext = toVec3d(math.getPosition(Math.min(s + EPS, length), false));
                tangent = pNext.subtract(center).normalize();
            }
        }
        Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();
        return List.of(center, tangent, normal);
    }
    public List<Vec3d> get() {
        return _get(i);
    }
    public List<Vec3d> get(int i) {
        if(reversed) {
            return _get(segment - i);
        } else {
            return _get(i);
        }
    }
}
```

## main\java\cn\myfrank\stationbuilder\PresetManager.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PresetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PRESET_PATH = FabricLoader.getInstance().getConfigDir().resolve("stationbuilder/presets");

    public static void savePreset(String name, int length, List<StationElement> elements) {
        try {
            File dir = PRESET_PATH.toFile();
            if (!dir.exists()) dir.mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("length", length);
            JsonArray elementArray = new JsonArray();
            for (StationElement e : elements) {
                // 将 NBT 转为字符串存入 JSON
                elementArray.add(e.toNbt().toString());
            }
            json.add("elements", elementArray);

            try (FileWriter writer = new FileWriter(new File(dir, name + ".json"))) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static List<String> getPresetList() {
        List<String> list = new ArrayList<>();
        File dir = PRESET_PATH.toFile();
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".json")) list.add(f.getName().replace(".json", ""));
            }
        }
        return list;
    }

    public static PresetData loadPreset(String name) {
        try {
            File file = new File(PRESET_PATH.toFile(), name + ".json");
            if (!file.exists()) return null;
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                int length = json.get("length").getAsInt();
                List<StationElement> elements = new ArrayList<>();
                JsonArray elementArray = json.getAsJsonArray("elements");
                for (int i = 0; i < elementArray.size(); i++) {
                    NbtCompound nbt = StringNbtReader.parse(elementArray.get(i).getAsString());
                    elements.add(StationElement.fromNbt(nbt));
                }
                return new PresetData(length, elements);
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    public record PresetData(int length, List<StationElement> elements) {}

    public static Path getPresetPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("stationbuilder/presets");
    }
}
```

## main\java\cn\myfrank\stationbuilder\RailBuilderConfig.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

public class RailBuilderConfig {
    public int railCount = 2;
    public double railSpacing = 4.5;
    public double ballastTopWidth = 5.0;
    public double ballastBottomWidth = 11.0;
    public int ballastMaxThickness = 4;
    public Identifier ballastBlock = new Identifier("minecraft", "andesite");
    public Identifier railType = StationBuilder.isMtrLoaded() ?
            new Identifier("mtr", "rail_connector_160") :
            new Identifier("minecraft", "rail");

    public int bridgeClearSpan = 50;
    public Identifier bridgeGuardRailBlock = new Identifier("minecraft", "stone_brick_wall");
    public Identifier bridgeBlock = new Identifier("minecraft", "smooth_stone");
    public Identifier bridgePillarBlock = new Identifier("minecraft", "light_gray_concrete");
    public double bridgeWidth = 7.0;

    public int tunnelHeight = 7;
    public Identifier tunnelWallBlock = new Identifier("minecraft", "stone");
    public Identifier tunnelCeilingBlock = new Identifier("minecraft", "light_gray_concrete");
    public Identifier tunnelFloorBlock = new Identifier("minecraft", "andesite");
    public double tunnelWidth = 7.0;
    public boolean clearFullHeight = false;

    public boolean useCatenary = true;
    public boolean isVanillaCatenary = !StationBuilder.isMsdLoaded();
    public int catenaryModeIndex = 0;
    public int catenarySpacing = 50;
    public Identifier catenaryBlock = StationBuilder.isMsdLoaded() ?
            new Identifier("msd", "catenary_connector") :
            new Identifier("minecraft", "cobweb");
    public Identifier catenaryBridgePillar = StationBuilder.isMsdLoaded() ?
            new Identifier("msd", "catenary_with_long") :
            new Identifier("minecraft", "stone_brick_wall");
    public Identifier catenaryTunnelPillar = StationBuilder.isMsdLoaded() ?
            new Identifier("msd", "catenary_with_long_top") :
            new Identifier("minecraft", "stone_brick_wall");

    // ===== NBT =====
    public static RailBuilderConfig fromItem(ItemStack stack) {
        RailBuilderConfig cfg = new RailBuilderConfig();
        if (stack.hasNbt() && stack.getNbt().contains("railBuilderConfig")) {
            cfg.fromNbt(stack.getNbt().getCompound("railBuilderConfig"));
        }
        return cfg;
    }

    public void saveToItem(ItemStack stack) {
        stack.getOrCreateNbt().put("railBuilderConfig", toNbt());
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        nbt.putInt("railCount", railCount);
        nbt.putDouble("railSpacing", railSpacing);
        nbt.putDouble("ballastTopWidth", ballastTopWidth);
        nbt.putDouble("ballastBottomWidth", ballastBottomWidth);
        nbt.putInt("ballastMaxThickness", ballastMaxThickness);
        nbt.putString("ballastBlock", ballastBlock.toString());
        nbt.putString("railType", railType.toString());

        nbt.putInt("bridgeClearSpan", bridgeClearSpan);
        nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlock.toString());
        nbt.putString("bridgeBlock", bridgeBlock.toString());
        nbt.putString("bridgePillarBlock", bridgePillarBlock.toString());
        nbt.putDouble("bridgeWidth", bridgeWidth);

        nbt.putInt("tunnelHeight", tunnelHeight);
        nbt.putString("tunnelWallBlock", tunnelWallBlock.toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlock.toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlock.toString());
        nbt.putDouble("tunnelWidth", tunnelWidth);
        nbt.putBoolean("clearFullHeight", clearFullHeight);

        nbt.putBoolean("useCatenary", useCatenary);
        nbt.putBoolean("isVanillaCatenary", isVanillaCatenary);
        nbt.putInt("catenaryModeIndex", catenaryModeIndex);
        nbt.putInt("catenarySpacing", catenarySpacing);
        nbt.putString("catenaryBlock", catenaryBlock.toString());
        nbt.putString("catenaryBridgePillar", catenaryBridgePillar.toString());
        nbt.putString("catenaryTunnelPillar", catenaryTunnelPillar.toString());

        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("railCount", NbtElement.INT_TYPE))
            railCount = nbt.getInt("railCount");

        if (nbt.contains("railSpacing", NbtElement.DOUBLE_TYPE))
            railSpacing = nbt.getDouble("railSpacing");

        if (nbt.contains("railType", NbtElement.STRING_TYPE))
            railType = new Identifier(nbt.getString("railType"));

        if (nbt.contains("ballastTopWidth", NbtElement.DOUBLE_TYPE))
            ballastTopWidth = nbt.getDouble("ballastTopWidth");

        if (nbt.contains("ballastBottomWidth", NbtElement.DOUBLE_TYPE))
            ballastBottomWidth = nbt.getDouble("ballastBottomWidth");

        if (nbt.contains("ballastMaxThickness", NbtElement.INT_TYPE))
            ballastMaxThickness = nbt.getInt("ballastMaxThickness");

        if (nbt.contains("ballastBlock", NbtElement.STRING_TYPE))
            ballastBlock = new Identifier(nbt.getString("ballastBlock"));

        if (nbt.contains("bridgeClearSpan", NbtElement.INT_TYPE))
            bridgeClearSpan = nbt.getInt("bridgeClearSpan");

        if (nbt.contains("bridgeGuardRailBlock", NbtElement.STRING_TYPE))
            bridgeGuardRailBlock = new Identifier(nbt.getString("bridgeGuardRailBlock"));

        if (nbt.contains("bridgeBlock", NbtElement.STRING_TYPE))
            bridgeBlock = new Identifier(nbt.getString("bridgeBlock"));

        if (nbt.contains("bridgePillarBlock", NbtElement.STRING_TYPE))
            bridgePillarBlock = new Identifier(nbt.getString("bridgePillarBlock"));

        if (nbt.contains("bridgeWidth", NbtElement.DOUBLE_TYPE))
            bridgeWidth = nbt.getDouble("bridgeWidth");

        if (nbt.contains("tunnelHeight", NbtElement.INT_TYPE))
            tunnelHeight = nbt.getInt("tunnelHeight");

        if (nbt.contains("tunnelWallBlock", NbtElement.STRING_TYPE))
            tunnelWallBlock = new Identifier(nbt.getString("tunnelWallBlock"));

        if (nbt.contains("tunnelCeilingBlock", NbtElement.STRING_TYPE))
            tunnelCeilingBlock = new Identifier(nbt.getString("tunnelCeilingBlock"));

        if (nbt.contains("tunnelFloorBlock", NbtElement.STRING_TYPE))
            tunnelFloorBlock = new Identifier(nbt.getString("tunnelFloorBlock"));
        
        if (nbt.contains("tunnelWidth", NbtElement.DOUBLE_TYPE))
            tunnelWidth = nbt.getDouble("tunnelWidth");

        if (nbt.contains("clearFullHeight"))
            clearFullHeight = nbt.getBoolean("clearFullHeight");

        if (nbt.contains("useCatenary"))
            useCatenary = nbt.getBoolean("useCatenary");

        if (nbt.contains("isVanillaCatenary"))
            isVanillaCatenary = nbt.getBoolean("isVanillaCatenary");
            
        if (nbt.contains("catenaryModeIndex", NbtElement.INT_TYPE))
            catenaryModeIndex = nbt.getInt("catenaryModeIndex");

        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE))
            catenarySpacing = nbt.getInt("catenarySpacing");

        if (nbt.contains("catenaryBlock", NbtElement.STRING_TYPE))
            catenaryBlock = new Identifier(nbt.getString("catenaryBlock"));

        if (nbt.contains("catenaryBridgePillar", NbtElement.STRING_TYPE))
            catenaryBridgePillar = new Identifier(nbt.getString("catenaryBridgePillar"));

        if (nbt.contains("catenaryTunnelPillar", NbtElement.STRING_TYPE))
            catenaryTunnelPillar = new Identifier(nbt.getString("catenaryTunnelPillar"));
    }
}

```

## main\java\cn\myfrank\stationbuilder\RailBuilderItem.java

```java
package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import static cn.myfrank.stationbuilder.StationBuilder.SYNC_AND_OPEN_PACKET_RAIL;
import static cn.myfrank.stationbuilder.StationBuilder.isMtrLoaded;

import java.util.List;

public class RailBuilderItem extends Item {
    public RailBuilderItem(Settings settings) {
        super(settings);
    }

    private void openGui(ServerPlayerEntity player, ItemStack stack) {
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        cfg.saveToItem(stack);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(cfg.toNbt());

        ServerPlayNetworking.send(player, SYNC_AND_OPEN_PACKET_RAIL, buf);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        if (!isMtrLoaded()) {
            if(world.isClient) {
                player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.no_mtr"), true);
            }
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);

        if (player.isSneaking()) {
            if (!world.isClient) {
                openGui((ServerPlayerEntity) player, stack);
            }
            return ActionResult.SUCCESS;
        }

        if (!world.isClient) {
            BlockPos pos = context.getBlockPos();
            var serverWorld = ((ServerPlayerEntity) player).getServerWorld();
            if (!MTRIntegration.isRailNode(serverWorld, pos) && !world.getBlockState(pos).isReplaceable()) {
                pos = pos.offset(context.getSide());
            }

            var last = RailBuilderState.getLastNodesAndAngle(stack);

            if (last == null) {
                var nodes = RailGenerator.placeFirstRailNodes(serverWorld, pos, player, cfg);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
                stack.getOrCreateNbt().putInt("CustomModelData", 1);
            } else {
                var nodes = RailGenerator.buildRails(serverWorld, last.left(), pos, cfg, player);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
                stack.getOrCreateNbt().putInt("CustomModelData", 1);
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) { // Shift
            if (!world.isClient) {
                openGui((ServerPlayerEntity) user, stack);
            }
            return TypedActionResult.success(stack);
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            World world,
            List<Text> tooltip,
            TooltipContext context
    ) {
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line1"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line2"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line3"));
        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null) return;
        var lastNodes = lastPair.left();
        if (lastNodes == null) return;
        BlockPos last = lastNodes.get(0);
        tooltip.add(
            Text.translatable(
                "tooltip.stationbuilder.start_pos",
                last.getX(),
                last.getY(),
                last.getZ()
            ).formatted(Formatting.GREEN)
        );
    }
}

```

## main\java\cn\myfrank\stationbuilder\RailBuilderState.java

```java
package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class RailBuilderState {

    private static final String KEY = "railBuilderState";
    private static final String LAST_NODES = "lastNodes";
    private static final String LAST_NODES_ANGLE = "lastNodesAngle";

    public static boolean isBuilding(ItemStack stack) {
        return getLastNodesAndAngle(stack) != null;
    }

    public static Pair<ArrayList<BlockPos>, Float> getLastNodesAndAngle(ItemStack stack) {
        if (!stack.hasNbt()) return null;

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(KEY)) return null;

        NbtCompound state = nbt.getCompound(KEY);
        if (!state.contains(LAST_NODES)) return null;

        NbtList list = state.getList(LAST_NODES, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return null;

        ArrayList<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound pos = list.getCompound(i);
            result.add(new BlockPos(
                    pos.getInt("x"),
                    pos.getInt("y"),
                    pos.getInt("z")
            ));
        }
        var angle = state.getFloat(LAST_NODES_ANGLE);
        return Pair.of(result, angle);
    }


    public static void setLastNodesAndAngle(ItemStack stack, @Nullable ArrayList<BlockPos> nodes, float angle) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound state = nbt.getCompound(KEY);

        NbtList list = new NbtList();
        if (nodes != null) {
            for (BlockPos p : nodes) {
                NbtCompound pos = new NbtCompound();
                pos.putInt("x", p.getX());
                pos.putInt("y", p.getY());
                pos.putInt("z", p.getZ());
                list.add(pos);
            }
        }

        state.put(LAST_NODES, list);
        state.putFloat(LAST_NODES_ANGLE, angle);
        nbt.put(KEY, state);
    }

    public static void clear(ItemStack stack) {
        if (!stack.hasNbt()) return;

        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(KEY)) {
            nbt.remove(KEY);
        }
    }
}

```

## main\java\cn\myfrank\stationbuilder\RailGenerator.java

```java
package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RailGenerator {
    public static void placeFirstRailNode(ServerWorld world, BlockPos pos, PlayerEntity player) {
        if (StationBuilder.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYaw());
            } else {
                System.out.println("Position is already a rail node: " + pos);
            }
        }
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        if (StationBuilder.isMtrLoaded()){
            Vec3d normal = RailMath.normalFromYaw(yaw);

            int count = config.railCount;
            double spacing = config.railSpacing;

            for (int i = 0; i < count; i++) {
                double offsetIndex = i - (count - 1) / 2.0;
                Vec3d offset = normal.multiply(offsetIndex * spacing);
                BlockPos s = RailMath.offsetPos(pos, offset);
                placedPositions.add(s);
            }
            return placedPositions;
        }
        return null;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerWorld world, BlockPos pos, PlayerEntity player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYaw(), config);
        if (nodes != null) {
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player);
            }
        }
        return nodes;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerWorld world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            PlayerEntity player
    ) {
        if (!StationBuilder.isMtrLoaded()) return null;

        float yaw = player.getYaw();
        Vec3d normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            Vec3d offset = normal.multiply(offsetIndex * spacing);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        if (startPositions.size() != endPositions.size()) {
            // 清除该玩家的轨道建造状态（服务端清除）
            ItemStack stack = player.getMainHandStack();
            RailBuilderState.clear(stack);
            stack.getOrCreateNbt().remove("CustomData"); // 若有其他标记
            return null;
        }

        // Place BlockNodes
        boolean anySuccess = false;
        for (int i = 0; i < count; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);
            if(MTRIntegration.isRailNode(world, s)) {
                boolean success = true;
                if (s.equals(e)) continue;
                if (!MTRIntegration.isRailNode(world, e)) {
                    MTRIntegration.placeRailNode(world, e, player.getYaw());
                }
                anySuccess |= success;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Validate rail type
        if (!MTRIntegration.isValidRailType(config.railType)) {
            player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            config.railType = MTRIntegration.getDefaultRailType();
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            var failToPlaceCatenaryNode = MTRIntegration.buildRails(startPositions, endPositions, player.getUuid(), world, config);
            if (failToPlaceCatenaryNode) {
                player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.catenary_node_failed"), true);
            }
        });

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
    }
}

```

## main\java\cn\myfrank\stationbuilder\RailMath.java

```java
package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RailMath {

    /**
     * 轨道前进方向（备用，后面做自动 yaw 会用到）
     */
    public static Vec3d directionFromYaw(float yaw) {
        // Minecraft yaw: 0 = south, 正方向是顺时针
        double rad = Math.toRadians(-yaw);
        return new Vec3d(Math.sin(rad), 0, Math.cos(rad));
    }

    /**
     * 轨道法向量（左-右方向，用于平行轨道）
     */
    public static Vec3d normalFromYaw(float yaw) {
        double rad = Math.toRadians(-yaw);
        // direction = ( sin, 0, cos )
        // normal    = ( cos, 0, -sin )
        return new Vec3d(Math.cos(rad), 0, -Math.sin(rad));
    }

    /**
     * 向量偏移并对齐到 BlockPos（对称 & 稳定）
     */
    public static BlockPos offsetPos(BlockPos origin, Vec3d offset) {
        return new BlockPos(
                (int)Math.round(origin.getX() + offset.x),
                origin.getY(),
                (int)Math.round(origin.getZ() + offset.z)
        );
    }

    
    public record PairXZ(int x, int z) {
        double distTo(Vec3d p) {
            double tx = x + 0.5, tz = z + 0.5;
            return Math.hypot(tx - p.x, tz - p.z);
        }
    }

    public static ArrayList<PairXZ> getPositions(Vec3d center, Vec3d normal, double halfWidth) {
        return getPositions(center, normal, halfWidth, halfWidth);
    }

    public static ArrayList<PairXZ> getPositions(Vec3d center, Vec3d normal, double leftWidth, double rightWidth) {
        ArrayList<PairXZ> left = new ArrayList<>(), right = new ArrayList<>();
        double EPS = 1e-4, step = 0.5, dx = normal.x, dz = normal.z;
        for (double s = 0; s <= leftWidth; s += step) {
            double actualS = Math.min(s, Math.max(0, leftWidth - EPS));
            var xzL = new PairXZ((int)Math.floor(center.x - actualS * dx), (int)Math.floor(center.z - actualS * dz));
            int leftSize = left.size();
            if (leftSize == 0 || !Objects.equals(left.get(leftSize - 1), xzL)) {
                left.add(xzL);
            }
        }
        for (double s = 0; s <= rightWidth; s += step) {
            double actualS = Math.min(s, Math.max(0, rightWidth - EPS));
            var xzR = new PairXZ((int)Math.floor(center.x + actualS * dx), (int)Math.floor(center.z + actualS * dz));
            int rightSize = right.size();
            if (rightSize == 0 || !Objects.equals(right.get(rightSize - 1), xzR)) {
                right.add(xzR);
            }
        }
        Collections.reverse(left);
        if (!left.isEmpty() && !right.isEmpty()){
            int leftLastIndex = left.size() - 1;
            if(Objects.equals(left.get(leftLastIndex), right.get(0))) {
                left.remove(leftLastIndex);
            }
            left.addAll(right);
        }
        return left;
    }

    /**
     * 判断两个点是否在直线的同一侧（忽略y坐标）
     * @param linePoint1 直线第一个点
     * @param linePoint2 直线第二个点
     * @param point1 要判断的第一个点
     * @param point2 要判断的第二个点
     * @return 1: 同侧, -1: 异侧, 0: 至少一个点在直线上
     */
    public static int getSideRelation(BlockPos linePoint1, BlockPos linePoint2,
                                      BlockPos point1, BlockPos point2) {
        long dx = linePoint2.getX() - linePoint1.getX();
        long dz = linePoint2.getZ() - linePoint1.getZ();

        long d1 = dx * (point1.getZ() - linePoint1.getZ())
                - dz * (point1.getX() - linePoint1.getX());

        long d2 = dx * (point2.getZ() - linePoint1.getZ())
                - dz * (point2.getX() - linePoint1.getX());

        if (d1 == 0 || d2 == 0) {
            return 0; // 点在直线上
        }

        if ((d1 > 0 && d2 > 0) || (d1 < 0 && d2 < 0)) {
            return 1; // 同侧
        } else {
            return -1; // 异侧
        }
    }

    public static boolean adjustPointSequence(ArrayList<BlockPos> fromNodes, ArrayList<BlockPos> toNodes) {
        int count = fromNodes.size();
        if (fromNodes.size() != toNodes.size()) {
            throw new IllegalArgumentException("Lists must have same size");
        }
        if (count > 1) {
            if (RailMath.getSideRelation(
                    fromNodes.get(0), toNodes.get(0),
                    fromNodes.get(count - 1), toNodes.get(count - 1)
            ) < 0) {
                Collections.reverse(fromNodes);
                return true;
            }
        }
        return false;
    }
}

```

## main\java\cn\myfrank\stationbuilder\SchematicLoaderUtil.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.schematic4j.SchematicLoader;
import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SchematicLoaderUtil {

    public static StructureTemplate loadSchematic(Path filePath) throws IOException, ParsingException {
        Schematic schematic = SchematicLoader.load(filePath.toString());

        int width = schematic.width();
        int height = schematic.height();
        int length = schematic.length();

        NbtCompound nbt = new NbtCompound();
        // 关键修复：加入 DataVersion，1.20 游戏需要这个版本号以确保正确的结构升级转换
        nbt.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());

        NbtList sizeList = new NbtList();
        sizeList.add(NbtInt.of(width));
        sizeList.add(NbtInt.of(height));
        sizeList.add(NbtInt.of(length));
        nbt.put("size", sizeList);

        NbtList blocksList = new NbtList();
        NbtList paletteList = new NbtList();
        Map<String, Integer> paletteMap = new HashMap<>();

        // 保留空气作为0索引是个好习惯
        NbtCompound airEntry = new NbtCompound();
        airEntry.putString("Name", "minecraft:air");
        paletteMap.put("minecraft:air{}", 0);
        paletteList.add(airEntry);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    var sBlock = schematic.block(x, y, z);
                    if (sBlock == null) continue;

                    String blockId = sBlock.block;

                    // 关键修复：直接跳过写入空气方块，避免 50x50x50 结构生成十几万无用数据卡死
                    if (blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air") || blockId.equals("minecraft:void_air")) {
                        continue;
                    }

                    // 使用包含属性的唯一键
                    String blockKey = blockId + sBlock.states.toString();

                    int stateId = paletteMap.computeIfAbsent(blockKey, k -> {
                        int id = paletteList.size();
                        NbtCompound paletteEntry = new NbtCompound();
                        paletteEntry.putString("Name", blockId);

                        // 关键修复：无损保留所有方块属性（如楼梯的 facing, half 等）
                        if (!sBlock.states.isEmpty()) {
                            NbtCompound propertiesNbt = new NbtCompound();
                            for (Map.Entry<String, String> entry : sBlock.states.entrySet()) {
                                propertiesNbt.putString(entry.getKey(), entry.getValue());
                            }
                            paletteEntry.put("Properties", propertiesNbt);
                        }

                        paletteList.add(paletteEntry);
                        return id;
                    });

                    NbtCompound blockNbt = new NbtCompound();
                    NbtList posList = new NbtList();
                    posList.add(NbtInt.of(x));
                    posList.add(NbtInt.of(y));
                    posList.add(NbtInt.of(z));
                    blockNbt.put("pos", posList);
                    blockNbt.putInt("state", stateId);

                    blocksList.add(blockNbt);
                }
            }
        }

        nbt.put("palette", paletteList);
        nbt.put("blocks", blocksList);
        nbt.put("entities", new NbtList());

        StructureTemplate template = new StructureTemplate();
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);

        return template;
    }

    private static BlockState convertBlockState(String blockString) {
        if (blockString == null || blockString.isEmpty()) {
            return Blocks.AIR.getDefaultState();
        }

        // schematic4j 的 name 包含附带的方块状态（如楼梯的朝向），我们用 "[" 截断只取其 ID 进行最基础的映射
        String blockId = blockString.split("\\[")[0];

        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return Blocks.AIR.getDefaultState();

        var block = Registries.BLOCK.get(id);

        // 如果注册表中找不到这个方块 (比如旧版模组的方块)，则用空气替代
        if (block == null || (block == Blocks.AIR && !blockId.equals("minecraft:air"))) {
            return Blocks.AIR.getDefaultState();
        }

        // 简单返回默认状态 (如果希望支持精确朝向，需要编写额外的字符串解析逻辑)
        return block.getDefaultState();
    }
}
```

## main\java\cn\myfrank\stationbuilder\StationBuilder.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class StationBuilder implements ModInitializer {
	public static final String MOD_ID = "stationbuilder";
	public static final int DEFAULT_STATION_LENGTH = 51;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier BUILD_PACKET_ID = new Identifier(MOD_ID, "build_station");
	public static final Identifier SYNC_AND_OPEN_PACKET = new Identifier(MOD_ID, "sync_open");
	public static final Identifier SAVE_DATA_PACKET = new Identifier(MOD_ID, "save_data");
	public static final Identifier SYNC_AND_OPEN_PACKET_RAIL = new Identifier(MOD_ID, "sync_open_rail");
	public static final Identifier SAVE_DATA_PACKET_RAIL = new Identifier(MOD_ID, "save_data_rail");
	public static final Identifier CLEAR_RAIL_PACKET = new Identifier("stationbuilder", "clear_rail_state");
	
	public static final ItemGroup STATION_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(MOD_ID, "station_group"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.stationbuilder.group")) // 语言文件中的名字
					.icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM)) // 图标
					.entries((displayContext, entries) -> {
						entries.add(ModBlocks.STATION_BUILDER_ITEM);
						entries.add(ModItems.RAIL_BUILDER_ITEM);
					})
					.build());

	private static final boolean hasMTR = FabricLoader.getInstance().isModLoaded("mtr");
	public static boolean isMtrLoaded() { return hasMTR; }

	private static final boolean hasMSD = FabricLoader.getInstance().isModLoaded("msd");
	public static boolean isMsdLoaded() { return hasMSD; }

	public static boolean isSoftTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				!state.getFluidState().isEmpty() || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
	}

	public static boolean isNotLiquidTransparent(BlockState state) {
		return state.isAir() || state.isReplaceable() ||
				state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		BuildingTemplateManager.loadTemplates();
		TickScheduler.init();
		
		ServerPlayNetworking.registerGlobalReceiver(SAVE_DATA_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			buf.readInt(); // facingInt, not used
			int length = buf.readInt();
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.elements = elements;
					be.markDirty();
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(
			SAVE_DATA_PACKET_RAIL,
			(server, player, handler, buf, responseSender) -> {
				NbtCompound nbt = buf.readNbt();
				server.execute(() -> {
					ItemStack stack = player.getMainHandStack();
					if (stack.getItem() instanceof RailBuilderItem && nbt != null) {
						RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
						cfg.fromNbt(nbt);
						cfg.saveToItem(stack);
					}
				});
			}
		);

		ServerPlayNetworking.registerGlobalReceiver(BUILD_PACKET_ID, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			int facingInt = buf.readInt();
			int length = buf.readInt();
			int elementCount = buf.readInt();

			List<StationElement> elements = new ArrayList<>();
			for (int i = 0; i < elementCount; i++) {
				elements.add(StationElement.read(buf));
			}

			server.execute(() -> {
				// 先存数据
				if (player.getWorld().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
					be.length = length;
					be.elements = elements;
					be.markDirty();
				}
				// 后建造
				StationGenerator.build(player, player.getServerWorld(), pos,
						Direction.fromHorizontal(facingInt), length, elements);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(
                StationBuilder.CLEAR_RAIL_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        ItemStack stack = player.getMainHandStack();

                        if (!stack.isEmpty()) {
                            RailBuilderState.clear(stack);
                            stack.getOrCreateNbt().remove("CustomModelData");
                            player.sendMessage(Text.translatable(
                                "message.stationbuilder.rail_builder.end"
                            ), true);
                        }
                    });
                }
        );
	}
}
```

## main\java\cn\myfrank\stationbuilder\StationBuilderBlock.java

```java
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
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
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
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
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
        return super.onBreak(world, pos, state, player);
    }
}
```

## main\java\cn\myfrank\stationbuilder\StationBuilderBlockEntity.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class StationBuilderBlockEntity extends BlockEntity {
    public int length = StationBuilder.DEFAULT_STATION_LENGTH;
    public List<StationElement> elements = new ArrayList<>();

    public StationBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STATION_BUILDER_ENTITY, pos, state);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("length", length);
        NbtList list = new NbtList();
        for (StationElement e : elements) list.add(e.toNbt());
        nbt.put("elements", list);
        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.length = nbt.getInt("length");
        this.elements.clear();
        NbtList list = nbt.getList("elements", 10);
        for (int i = 0; i < list.size(); i++) {
            this.elements.add(StationElement.fromNbt(list.getCompound(i)));
        }
    }
}
```

## main\java\cn\myfrank\stationbuilder\StationGenerator.java

```java
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
        Optional<StructureTemplate> custom = BuildingTemplateManager.getTemplate(element.presetName);
        StructureTemplate template = null;

        if (custom.isPresent()) {
            template = custom.get();
        } else {
            StructureTemplateManager manager = world.getStructureTemplateManager();
            Identifier templateId;
            if (element.presetName.contains(":")) {
                templateId = new Identifier(element.presetName);
            } else {
                templateId = new Identifier("stationbuilder", element.presetName);
            }
            template = manager.getTemplate(templateId).orElse(null);
        }

        Direction right = facing.rotateYClockwise();

        if (template != null) {
            BlockRotation rot = getRotationFromDirection(facing);
            StructurePlacementData data = new StructurePlacementData()
                    .setRotation(rot)
                    .setMirror(net.minecraft.util.BlockMirror.NONE);

            net.minecraft.util.math.Vec3i size = template.getSize();
            int sx = size.getX();
            int sz = size.getZ();

            // 1. 模拟旋转，计算结构旋转后的四个角落 (在局部坐标系下)
            int[][] corners = {
                    {0, 0},
                    {sx, 0},
                    {0, sz},
                    {sx, sz}
            };

            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (int[] corner : corners) {
                int cx = corner[0];
                int cz = corner[1];
                int rx = cx, rz = cz;
                // 原版 StructureTemplate.transform 的旋转矩阵规律
                switch(rot) {
                    case CLOCKWISE_90:  rx = -cz; rz = cx;  break;
                    case CLOCKWISE_180: rx = -cx; rz = -cz; break;
                    case COUNTERCLOCKWISE_90: rx = cz; rz = -cx; break;
                    case NONE:
                    default: break;
                }
                if (rx < minX) minX = rx;
                if (rx > maxX) maxX = rx;
                if (rz < minZ) minZ = rz;
                if (rz > maxZ) maxZ = rz;
            }

            // 2. 找到分配给该建筑的真实目标中心点 (World Coordinate)
            // 修正：(W - 1) / 2.0 是准确获取分配空间正中心点的公式
            double targetX = pos.getX() + 0.5 + right.getOffsetX() * (element.getWidth() - 1) / 2.0 + facing.getOffsetX() * (length - 1) / 2.0;
            double targetZ = pos.getZ() + 0.5 + right.getOffsetZ() * (element.getWidth() - 1) / 2.0 + facing.getOffsetZ() * (length - 1) / 2.0;

            // 3. 反推起始放置点：目标中心 减去 旋转后结构的内部中心
            double placeX = targetX - (minX + maxX) / 2.0;
            double placeZ = targetZ - (minZ + maxZ) / 2.0;

            BlockPos placePos = BlockPos.ofFloored(placeX, pos.getY(), placeZ);

            // 4. 放置结构：传入 BlockPos.ORIGIN 作为 pivot，让游戏底层乖乖绕 (0,0,0) 旋转，我们外部在坐标上完全补偿它
            template.place(world, placePos, BlockPos.ORIGIN, data, world.random, 2);
        } else {
            // == 找不到模板时的回退火柴盒 ==
            world.getPlayers().forEach(p -> p.sendMessage(net.minecraft.text.Text.literal("Template not found: " + element.presetName + ", building matchbox.").formatted(net.minecraft.util.Formatting.RED), false));

            int buildingWidth = 8; // 沿 right 方向
            int buildingDepth = 12; // 沿 facing 方向
            int buildingHeight = 6;

            int depthOffset = (length - buildingDepth) / 2;
            int widthOffset = (element.getWidth() - buildingWidth) / 2;

            // 将手工生成的火柴盒也进行居中（加上了之前遗漏的 widthOffset）
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
                if (p.hasPids && l > 0 && l < length - 1 && pillarHere && StationBuilder.isMtrLoaded()) {
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
```

## main\java\cn\myfrank\stationbuilder\TestConnectResult.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public record TestConnectResult(boolean success, double radius, double length, ArrayList<Vec3d> positions) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("success", success);
        nbt.putDouble("radius", radius);
        nbt.putDouble("length", length);

        if (positions != null) {
            NbtList positionsList = new NbtList();
            for (Vec3d pos : positions) {
                if (pos != null) {
                    NbtCompound posNbt = new NbtCompound();
                    posNbt.putDouble("x", pos.getX());
                    posNbt.putDouble("y", pos.getY());
                    posNbt.putDouble("z", pos.getZ());
                    positionsList.add(posNbt);
                }
            }
            nbt.put("positions", positionsList);
        } else {
            nbt.put("positions", new NbtList());
        }

        return nbt;
    }

    public static TestConnectResult fromNbt(@NotNull NbtCompound nbt) {
        boolean success = nbt.getBoolean("success");
        double radius = nbt.getDouble("radius");
        double length = nbt.getDouble("length");
        ArrayList<Vec3d> positions = new ArrayList<>();

        if (nbt.contains("positions", NbtElement.LIST_TYPE)) {
            NbtList positionsList = nbt.getList("positions", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < positionsList.size(); i++) {
                NbtCompound posNbt = positionsList.getCompound(i);
                if (posNbt.contains("x") && posNbt.contains("y") && posNbt.contains("z")) {
                    double x = posNbt.getDouble("x");
                    double y = posNbt.getDouble("y");
                    double z = posNbt.getDouble("z");
                    positions.add(new Vec3d(x, y, z));
                }
            }
        }

        return new TestConnectResult(success, radius, length,positions);
    }
}
```

## main\java\cn\myfrank\stationbuilder\TickScheduler.java

```java
package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickScheduler {
    private static final List<DelayedTask> tasks = new ArrayList<>();
    private static final List<Runnable> toRun = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            // 1. tick 计数 & 收集要执行的任务
            Iterator<DelayedTask> it = tasks.iterator();
            while (it.hasNext()) {
                DelayedTask task = it.next();
                if (--task.remainingTicks <= 0) {
                    toRun.add(task.action);
                    it.remove();
                }
            }

            // 2. 真正执行（此时 tasks 已不在迭代中）
            for (Runnable action : toRun) {
                action.run();
            }
            toRun.clear();
        });
    }

    public static void schedule(int ticks, Runnable action) {
        tasks.add(new DelayedTask(ticks, action));
    }

    private static class DelayedTask {
        int remainingTicks;
        Runnable action;

        DelayedTask(int ticks, Runnable action) {
            this.remainingTicks = ticks;
            this.action = action;
        }
    }
}

```

## main\java\cn\myfrank\stationbuilder\elements\BuildingElement.java

```java
package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.BuildingTemplateManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

// 站房：使用预设名称
public class BuildingElement extends StationElement {
    public String presetName;
    public BuildingElement(String name) { this.presetName = name; }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // BUILDING
        nbt.putString("preset", presetName);
        return nbt;
    }

    @Override public Type getType() { return Type.BUILDING; }
    @Override public int getWidth() {
        return BuildingTemplateManager.getTemplate(presetName)
                .map(t -> t.getSize().getX())
                .orElse(8); // 如果没找到模板，默认8宽
    }
    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeString(presetName);
    }
}
```

## main\java\cn\myfrank\stationbuilder\elements\PlatformElement.java

```java
package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class PlatformElement extends StationElement {
    public static final int MAX_BLOCK_COUNT = 5;
    public int width = 9;
    public Identifier safetyBlock = StationBuilder.isMtrLoaded() ?
            new Identifier("mtr", "platform") :
            new Identifier("minecraft", "yellow_concrete");

    public static class MixSlot {
        public Identifier blockId;
        public double weight;
        public MixSlot() {
            blockId = new Identifier("minecraft", "smooth_stone");
            weight = 1.0;
        }
        public MixSlot(Identifier blockId, double weight) {
            this.blockId = blockId;
            this.weight = weight;
        }
    }

    public MixSlot[] mixSlots = new MixSlot[5];

    // 雨棚开关与基础属性
    public boolean hasCanopy = true;
    public int canopyHeight = 4; // 距离站台地面的高度
    public Identifier canopySlabId = new Identifier("minecraft", "smooth_stone_slab");
    public Identifier pillarBlockId = new Identifier("minecraft", "stone_brick_wall");

    public enum CanopyStyle { FLAT, INVERTED_V, V_SHAPE, SLANT_RIGHT, SLANT_LEFT, PILLAR_ONLY }
    public CanopyStyle canopyStyle = CanopyStyle.V_SHAPE;

    public enum PillarStyle { SINGLE, DOUBLE, NONE }
    public PillarStyle pillarStyle = PillarStyle.SINGLE;

    public int pillarSpacing = 9;     // 支柱间距
    public int firstPillarOffset = 0; // 首个支柱偏移量

    public boolean hasLighting = true;
    public Identifier lightBlockId = new Identifier("minecraft", "sea_lantern");

    public boolean hasShieldDoors = true;
    public int doorStartOffset = 2;
    public int doorSpacing = 3;
    public Identifier psdEndId = new Identifier("mtr", "apg_glass_end");
    public Identifier psdGlassId = new Identifier("mtr", "apg_glass"); // 示例
    public Identifier psdDoorId = new Identifier("mtr", "apg_door");

    public boolean hasPids = true;
    public Identifier pidBlockId = new Identifier("mtr", "pids_1");
    public Identifier pidPoleId = new Identifier("mtr", "pids_pole");

    public PlatformElement() {
        for (int i = 0; i < MAX_BLOCK_COUNT; i++) mixSlots[i] = new MixSlot();
        // 默认只有第一个槽位有方块，其余设为空气或默认值
        for (int i = 1; i < MAX_BLOCK_COUNT; i++) {
            mixSlots[i].blockId = new Identifier("minecraft", "air");
            mixSlots[i].weight = 0.0;
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // PLATFORM
        nbt.putInt("width", width);
        nbt.putString("safety", safetyBlock.toString());

        // 创建一个列表来存储混合槽位
        NbtList mixList = new NbtList();
        for (MixSlot slot : mixSlots) {
            NbtCompound slotNbt = new NbtCompound();
            slotNbt.putString("id", slot.blockId.toString());
            slotNbt.putDouble("weight", slot.weight);
            mixList.add(slotNbt);
        }
        nbt.put("mix", mixList);
        nbt.putBoolean("hasCanopy", hasCanopy);
        nbt.putInt("canopyHeight", canopyHeight);
        nbt.putString("canopySlabId", canopySlabId.toString());
        nbt.putString("pillarBlockId", pillarBlockId.toString());
        nbt.putString("canopyStyle", canopyStyle.name());
        nbt.putString("pillarStyle", pillarStyle.name());
        nbt.putInt("pillarSpacing", pillarSpacing);
        nbt.putInt("firstPillarOffset", firstPillarOffset);
        nbt.putBoolean("hasLighting", hasLighting);
        nbt.putString("lightBlockId", lightBlockId.toString());
        nbt.putBoolean("hasShieldDoors", hasShieldDoors);
        nbt.putInt("doorStartOffset", doorStartOffset);
        nbt.putInt("doorSpacing", doorSpacing);
        nbt.putString("psdEndId", psdEndId.toString());
        nbt.putString("psdGlassId", psdGlassId.toString());
        nbt.putString("psdDoorId", psdDoorId.toString());
        nbt.putBoolean("hasPids", hasPids);
        nbt.putString("pidBlockId", pidBlockId.toString());
        nbt.putString("pidPoleId", pidPoleId.toString());
        return nbt;
    }

    @Override public Type getType() { return Type.PLATFORM; }
    @Override public int getWidth() { return width; }
    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeInt(width);
        buf.writeIdentifier(safetyBlock);
        for (MixSlot slot : mixSlots) {
            buf.writeIdentifier(slot.blockId);
            buf.writeDouble(slot.weight);
        }
        buf.writeBoolean(hasCanopy);
        buf.writeInt(canopyHeight);
        buf.writeIdentifier(canopySlabId);
        buf.writeIdentifier(pillarBlockId);
        buf.writeEnumConstant(canopyStyle);
        buf.writeEnumConstant(pillarStyle);
        buf.writeInt(pillarSpacing);
        buf.writeInt(firstPillarOffset);
        buf.writeBoolean(hasLighting);
        buf.writeIdentifier(lightBlockId);
        buf.writeBoolean(hasShieldDoors);
        buf.writeInt(doorStartOffset);
        buf.writeInt(doorSpacing);
        buf.writeIdentifier(psdEndId);
        buf.writeIdentifier(psdGlassId);
        buf.writeIdentifier(psdDoorId);
        buf.writeBoolean(hasPids);
        buf.writeIdentifier(pidBlockId);
        buf.writeIdentifier(pidPoleId);
    }
}
```

## main\java\cn\myfrank\stationbuilder\elements\StationElement.java

```java
package cn.myfrank.stationbuilder.elements;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

public abstract class StationElement {
    public abstract NbtCompound toNbt();
    // 在 StationElement.java 中添加
    public static StationElement fromNbt(NbtCompound nbt) {
        String typeStr = nbt.getString("type");
        Type type = Type.valueOf(typeStr);

        switch (type) {
            case TRACK:
                TrackElement track = new TrackElement();
                if (nbt.contains("ballast")) {
                    track.ballastBlock = new net.minecraft.util.Identifier(nbt.getString("ballast"));
                    track.isMtrTrack = nbt.getBoolean("isMtrTrack");
                }
                return track;

            case PLATFORM:
                PlatformElement p = new PlatformElement();
                p.width = nbt.getInt("width");
                p.safetyBlock = new net.minecraft.util.Identifier(nbt.getString("safety"));

                if (nbt.contains("mix")) {
                    NbtList mixList = nbt.getList("mix", 10); // 10 是 COMPOUND 的类型 ID
                    for (int i = 0; i < Math.min(mixList.size(), 5); i++) {
                        NbtCompound slotNbt = mixList.getCompound(i);
                        p.mixSlots[i].blockId = new net.minecraft.util.Identifier(slotNbt.getString("id"));
                        p.mixSlots[i].weight = slotNbt.getDouble("weight");
                    }
                }
                p.hasCanopy = nbt.getBoolean("hasCanopy");
                p.canopyHeight = nbt.getInt("canopyHeight");
                p.canopySlabId = new net.minecraft.util.Identifier(nbt.getString("canopySlabId"));
                p.pillarBlockId = new net.minecraft.util.Identifier(nbt.getString("pillarBlockId"));
                p.canopyStyle = PlatformElement.CanopyStyle.valueOf(nbt.getString("canopyStyle"));
                p.pillarStyle = PlatformElement.PillarStyle.valueOf(nbt.getString("pillarStyle"));
                p.pillarSpacing = nbt.getInt("pillarSpacing");
                p.firstPillarOffset = nbt.getInt("firstPillarOffset");
                p.hasLighting = nbt.getBoolean("hasLighting");
                p.lightBlockId = new net.minecraft.util.Identifier(nbt.getString("lightBlockId"));
                p.hasShieldDoors = nbt.getBoolean("hasShieldDoors");
                p.doorStartOffset = nbt.getInt("doorStartOffset");
                p.doorSpacing = nbt.getInt("doorSpacing");
                p.psdEndId = new net.minecraft.util.Identifier(nbt.getString("psdEndId"));
                p.psdGlassId = new net.minecraft.util.Identifier(nbt.getString("psdGlassId"));
                p.psdDoorId = new net.minecraft.util.Identifier(nbt.getString("psdDoorId"));
                p.hasPids = nbt.getBoolean("hasPids");
                p.pidBlockId = new net.minecraft.util.Identifier(nbt.getString("pidBlockId"));
                if (nbt.contains("pidPoleId")) {
                    p.pidPoleId = new net.minecraft.util.Identifier(nbt.getString("pidPoleId"));
                }
                return p;

            case BUILDING:
                String preset = nbt.getString("preset");
                return new BuildingElement(preset.isEmpty() ? "matchbox" : preset);

            default:
                throw new IllegalArgumentException("Unknown element type_: " + typeStr);
        }
    }

    public enum Type { TRACK, PLATFORM, BUILDING }

    public abstract Type getType();
    public abstract int getWidth();

    // 将元素序列化到网络缓冲区
    public abstract void write(PacketByteBuf buf);

    // 从缓冲区读取元素
    public static StationElement read(PacketByteBuf buf) {
        Type type = buf.readEnumConstant(Type.class);
        return switch (type) {
            case TRACK -> {
                TrackElement track = new TrackElement();
                track.ballastBlock = buf.readIdentifier(); // 读取路基方块ID
                track.isMtrTrack = buf.readBoolean();
                yield track;
            }
            case PLATFORM -> {
                PlatformElement p = new PlatformElement();
                p.width = buf.readInt();
                p.safetyBlock = buf.readIdentifier();
                for(int i = 0; i < PlatformElement.MAX_BLOCK_COUNT; i++) {
                    p.mixSlots[i] = new PlatformElement.MixSlot(
                            buf.readIdentifier(),
                            buf.readDouble()
                    );
                }
                p.hasCanopy = buf.readBoolean();
                p.canopyHeight = buf.readInt();
                p.canopySlabId = buf.readIdentifier();
                p.pillarBlockId = buf.readIdentifier();
                p.canopyStyle = buf.readEnumConstant(PlatformElement.CanopyStyle.class);
                p.pillarStyle = buf.readEnumConstant(PlatformElement.PillarStyle.class);
                p.pillarSpacing = buf.readInt();
                p.firstPillarOffset = buf.readInt();
                p.hasLighting = buf.readBoolean();
                p.lightBlockId = buf.readIdentifier();
                p.hasShieldDoors = buf.readBoolean();
                p.doorStartOffset = buf.readInt();
                p.doorSpacing = buf.readInt();
                p.psdEndId = buf.readIdentifier();
                p.psdGlassId = buf.readIdentifier();
                p.psdDoorId = buf.readIdentifier();
                p.hasPids = buf.readBoolean();
                p.pidBlockId = buf.readIdentifier();
                p.pidPoleId = buf.readIdentifier();
                yield p;
            }
            case BUILDING -> new BuildingElement(buf.readString());
        };
    }
}
```

## main\java\cn\myfrank\stationbuilder\elements\TrackElement.java

```java
package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

// 股道：固定3格宽
// --- TrackElement 类内部 ---
public class TrackElement extends StationElement {
    public boolean isMtrTrack = StationBuilder.isMtrLoaded();
    public Identifier ballastBlock = new Identifier("minecraft", "andesite"); // 默认路基为砾石

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeIdentifier(ballastBlock); // 写入路基方块ID
        buf.writeBoolean(isMtrTrack);
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // 存储枚举名：TRACK
        nbt.putString("ballast", ballastBlock.toString()); // 存储 Identifier 字符串
        nbt.putBoolean("isMtrTrack", isMtrTrack);
        return nbt;
    }
}
```

## main\java\cn\myfrank\stationbuilder\mixin\StationBuilderMixinPlugin.java

```java
package cn.myfrank.stationbuilder.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class StationBuilderMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 如果 Mixin 类名包含 ".mtr."，则检查 mtr 是否加载
        if (mixinClassName.contains(".mtr.")) {
            return FabricLoader.getInstance().isModLoaded("mtr");
        }
        return true;
    }

    // 以下方法保持默认即可
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
```

## main\java\cn\myfrank\stationbuilder\schematic4j\SchematicFormat.java

```java
package cn.myfrank.stationbuilder.schematic4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.myfrank.stationbuilder.schematic4j.exception.NoParserFoundException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.parser.LitematicaParser;
import cn.myfrank.stationbuilder.schematic4j.parser.Parser;
import cn.myfrank.stationbuilder.schematic4j.parser.SchematicaParser;
import cn.myfrank.stationbuilder.schematic4j.parser.SpongeParser;

public enum SchematicFormat {
	SPONGE_V1("schem", SpongeParser::new),
	SPONGE_V2("schem", SpongeParser::new),
	SPONGE_V3("schem", SpongeParser::new),
	LITEMATICA("litematic", LitematicaParser::new),
	SCHEMATICA("schematic", SchematicaParser::new),
	UNKNOWN;

	private static final Logger log = LoggerFactory.getLogger(SchematicFormat.class);

	public final String fileExtension;
	private final Supplier<Parser> parserGenerator;

	SchematicFormat(String fileExtension, Supplier<Parser> parserGenerator) {
		this.fileExtension = fileExtension;
		this.parserGenerator = parserGenerator;
	}

	SchematicFormat(String fileExtension) {
		this(fileExtension, null);
	}

	SchematicFormat() {
		this(null, null);
	}

	public Parser createParser() throws NoParserFoundException {
		if (parserGenerator == null)
			throw new NoParserFoundException(this);

		return parserGenerator.get();
	}

	/**
	 * Tries to guess the schematic format of the input.
	 *
	 * @param nbt The NBT input to check
	 * @return The format guesses from looking at the input, or {@link SchematicFormat#UNKNOWN} if no known format was found.
	 */
	public static @NotNull SchematicFormat guessFormat(@Nullable CompoundTag nbt) {
		if (nbt == null) {
			return SchematicFormat.UNKNOWN;
		}

		final Candidates<SchematicFormat> candidates = new Candidates<>();
		guessSpongeFormat(candidates, nbt);
		guessLitematicaFormat(candidates, nbt);
		guessSchematicaFormat(candidates, nbt);

		final SchematicFormat guess = candidates.best().orElse(SchematicFormat.UNKNOWN);
		log.debug("Guessed {} as the format", guess);
		return guess;
	}

	private static void guessSpongeFormat(Candidates<SchematicFormat> candidates, @NotNull CompoundTag rootTag) {
		if (rootTag.containsKey(SpongeParser.NBT_VERSION)) {
			final int version = rootTag.getInt(SpongeParser.NBT_VERSION);
			switch (version) {
				case 1:
					candidates.increment(SchematicFormat.SPONGE_V1, 5);
				case 2:
					candidates.increment(SchematicFormat.SPONGE_V2, 5);
				case 3:
					candidates.increment(SchematicFormat.SPONGE_V3, 5);
			}
		} else {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.exclude(SchematicFormat.SPONGE_V2);
			candidates.exclude(SchematicFormat.SPONGE_V3);
		}
		if (rootTag.containsKey(SpongeParser.NBT_DATA_VERSION)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		} else {
			candidates.exclude(SchematicFormat.SPONGE_V2);
			candidates.exclude(SchematicFormat.SPONGE_V3);
		}
		if (rootTag.containsKey(SpongeParser.NBT_WIDTH)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_HEIGHT)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_LENGTH)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_PALETTE)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_PALETTE_MAX)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_BLOCK_DATA)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_BIOME_DATA)) {
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_TILE_ENTITIES)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_BLOCK_ENTITIES)) {
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_V3_BLOCKS)) {
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_V3_BIOMES)) {
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
		if (rootTag.containsKey(SpongeParser.NBT_METADATA)) {
			candidates.increment(SchematicFormat.SPONGE_V1, 1);
			candidates.increment(SchematicFormat.SPONGE_V2, 1);
			candidates.increment(SchematicFormat.SPONGE_V3, 1);
		}
	}

	private static void guessLitematicaFormat(Candidates<SchematicFormat> candidates, @NotNull CompoundTag nbt) {
		if (nbt.containsKey(LitematicaParser.NBT_MINECRAFT_DATA_VERSION)) {
			candidates.increment(SchematicFormat.LITEMATICA, 1);
		}
		if (nbt.containsKey(LitematicaParser.NBT_VERSION)) {
			candidates.increment(SchematicFormat.LITEMATICA, 1);
		}
		if (nbt.containsKey(LitematicaParser.NBT_METADATA)) {
			candidates.increment(SchematicFormat.LITEMATICA, 1);
		}
		if (nbt.containsKey(LitematicaParser.NBT_REGIONS)) {
			candidates.increment(SchematicFormat.LITEMATICA, 2);
		} else {
			candidates.exclude(SchematicFormat.LITEMATICA);
		}
	}

	private static void guessSchematicaFormat(Candidates<SchematicFormat> candidates, @NotNull CompoundTag nbt) {
		if (nbt.containsKey(SchematicaParser.NBT_MAPPING_SCHEMATICA)) {
			candidates.increment(SchematicFormat.SCHEMATICA, 10);
		} else {
			candidates.exclude(SchematicFormat.SCHEMATICA);
		}
		if (nbt.containsKey(SchematicaParser.NBT_WIDTH)) {
			candidates.increment(SchematicFormat.SCHEMATICA, 1);
		} else {
			candidates.exclude(SchematicFormat.SCHEMATICA);
		}
		if (nbt.containsKey(SchematicaParser.NBT_HEIGHT)) {
			candidates.increment(SchematicFormat.SCHEMATICA, 1);
		} else {
			candidates.exclude(SchematicFormat.SCHEMATICA);
		}
		if (nbt.containsKey(SchematicaParser.NBT_LENGTH)) {
			candidates.increment(SchematicFormat.SCHEMATICA, 1);
		} else {
			candidates.exclude(SchematicFormat.SCHEMATICA);
		}
	}

	protected static class Candidates<T> {
		protected final Map<T, Integer> candidates = new HashMap<>();
		protected final Set<T> excluded = new HashSet<>();

		public void increment(T candidate, int weight) {
			if (!excluded.contains(candidate)) {
				final int currentWeight = candidates.getOrDefault(candidate, 0);
				candidates.put(candidate, currentWeight + weight);
				log.trace("Format candidate {} prioritized by {} (total: {})", candidate, weight, currentWeight + weight);
			}
		}

		public void exclude(T candidate) {
			log.trace("Excluded format: {}", candidate);
			excluded.add(candidate);
			candidates.remove(candidate);
		}

		public Optional<T> best() {
			final Optional<Map.Entry<T, Integer>> best = candidates.entrySet().stream().reduce((a, b) -> {
				if (a.getValue() >= b.getValue()) {
					return a;
				} else {
					return b;
				}
			});
			log.trace("Format candidates: {}", candidates);
			log.trace("Excluded formats: {}", excluded);
			log.trace("Best candidate: {}", best);
			return best.map(Map.Entry::getKey);
		}

		@Override
		public String toString() {
			return "Candidates[candidates=" + candidates + ", excluded=" + excluded + ']';
		}
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\SchematicLoader.java

```java
package cn.myfrank.stationbuilder.schematic4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.io.NBTUtil;
import cn.myfrank.stationbuilder.schematic4j.nbt.io.NamedTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.parser.Parser;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;

/**
 * A collection of utility methods to load and parse schematics.
 */
public class SchematicLoader {

	private static final Logger log = LoggerFactory.getLogger(SchematicLoader.class);

	private SchematicLoader() {}

	/**
	 * Load a schematic from an input stream.
	 *
	 * @param is The input stream to load the schematic from.
	 * @return The loaded and parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 * @throws IOException in case of I/O error
	 * @see SchematicLoader#load(Path)
	 * @see SchematicLoader#load(File)
	 * @see SchematicLoader#load(String)
	 */
	public static @NotNull Schematic load(@NotNull InputStream is) throws ParsingException, IOException {
		final NamedTag rootTag = NBTUtil.Reader.read().from(is);
		return parse(rootTag);
	}

	/**
	 * Load a schematic from a file.
	 *
	 * @param path The file to load the schematic from.
	 * @return The loaded and parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 * @throws IOException in case of I/O error
	 * @see SchematicLoader#load(InputStream)
	 * @see SchematicLoader#load(File)
	 * @see SchematicLoader#load(String)
	 */
	public static @NotNull Schematic load(@NotNull Path path) throws ParsingException, IOException {
		try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
			return load(is);
		}
	}

	/**
	 * Load a schematic from a file.
	 *
	 * @param file The file to load the schematic from.
	 * @return The loaded and parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 * @throws IOException in case of I/O error
	 * @see SchematicLoader#load(InputStream)
	 * @see SchematicLoader#load(Path)
	 * @see SchematicLoader#load(String)
	 */
	public static @NotNull Schematic load(@NotNull File file) throws ParsingException, IOException {
		return load(file.toPath());
	}

	/**
	 * Load a schematic from a file.
	 *
	 * @param filePath The file path to load the schematic from.
	 * @return The loaded and parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 * @throws IOException in case of I/O error
	 * @see SchematicLoader#load(InputStream)
	 * @see SchematicLoader#load(Path)
	 * @see SchematicLoader#load(File)
	 */
	public static @NotNull Schematic load(@NotNull String filePath) throws ParsingException, IOException {
		return load(Paths.get(filePath));
	}

	/**
	 * Attempts to guess the schematic format and parse the input.
	 * <br>
	 * If you already know the format, consider parsing the NBT tag directly - i.e. {@code SchematicFormat.SPONGE_V2.createParser().parse(nbt)}.
	 *
	 * @param nbt The NBT root tag to parse.
	 * @return The parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 */
	public static @NotNull Schematic parse(@Nullable CompoundTag nbt) throws ParsingException {
		SchematicFormat format = SchematicFormat.guessFormat(nbt);
		log.info("Found format: {}", format);

		Parser parser = format.createParser();
		log.debug("Found parser: {}", parser);

		return parser.parse(nbt);
	}

	/**
	 * Attempts to guess the schematic format and parse the input.
	 * <br>
	 * If you already know the format, consider parsing the NBT tag directly - i.e. {@code SchematicFormat.SPONGE_V2.createParser().parse(nbt)}.
	 *
	 * @param input The NBT root tag to parse.
	 * @return The parsed schematic
	 * @throws ParsingException in case no supported parses was found or there was a parsing error
	 */
	public static @NotNull Schematic parse(@Nullable NamedTag input) throws ParsingException {
		CompoundTag nbt = input != null && input.getTag() instanceof CompoundTag ? (CompoundTag) input.getTag() : null;

		// === 核心修复：解开 WorldEdit 生成的包裹层 ===
		if (nbt != null) {
			// 如果外层没有 Width 标签，但包含一个名为 Schematic 的子节点，说明被包裹了
			if (!nbt.containsKey("Width") && !nbt.containsKey("Version") && nbt.containsKey("Schematic")) {
				cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag<?> inner = nbt.get("Schematic");
				if (inner instanceof CompoundTag) {
					nbt = (CompoundTag) inner; // 提取出真正的数据层
				}
			}
		}
		// ===========================================

		return parse(nbt);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\exception\MissingFieldException.java

```java
package cn.myfrank.stationbuilder.schematic4j.exception;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class MissingFieldException extends ParsingException {

	public final Tag<?> tag;
	public final String field;
	public final Class<?> fieldType;

	public MissingFieldException(Tag<?> tag, String field, Class<?> fieldType) {
		super("Tag is missing field '" + field + "' of type " + fieldType.getSimpleName());
		this.tag = tag;
		this.field = field;
		this.fieldType = fieldType;
	}

	public MissingFieldException(Tag<?> tag, String field, Class<?> fieldType, Throwable cause) {
		super("Tag is missing field '" + field + "' of type " + fieldType.getSimpleName(), cause);
		this.tag = tag;
		this.field = field;
		this.fieldType = fieldType;
	}

}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\exception\NoParserFoundException.java

```java
package cn.myfrank.stationbuilder.schematic4j.exception;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;

public class NoParserFoundException extends ParsingException {

	public final SchematicFormat format;


	public NoParserFoundException() {
		super("No suitable parser found");
		this.format = null;
	}

	public NoParserFoundException(SchematicFormat format) {
		super("No suitable parser found for format " + format);
		this.format = format;
	}

	public NoParserFoundException(SchematicFormat format, Throwable cause) {
		super("No suitable parser found for format " + format, cause);
		this.format = format;
	}

	public NoParserFoundException(Throwable cause) {
		super("No suitable parser found", cause);
		this.format = null;
	}

	public NoParserFoundException(SchematicFormat format, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super("No suitable parser found for format " + format, cause, enableSuppression, writableStackTrace);
		this.format = format;
	}

}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\exception\ParsingException.java

```java
package cn.myfrank.stationbuilder.schematic4j.exception;

public class ParsingException extends Exception {

	public ParsingException() {
	}

	public ParsingException(String message) {
		super(message);
	}

	public ParsingException(String message, Throwable cause) {
		super(message, cause);
	}

	public ParsingException(Throwable cause) {
		super(cause);
	}

	public ParsingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\exception\SchematicBuilderException.java

```java
package cn.myfrank.stationbuilder.schematic4j.exception;

public class SchematicBuilderException extends IllegalArgumentException {

	public SchematicBuilderException() {
	}

	public SchematicBuilderException(String message) {
		super(message);
	}

	public SchematicBuilderException(String message, Throwable cause) {
		super(message, cause);
	}

	public SchematicBuilderException(Throwable cause) {
		super(cause);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\Deserializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;

/**
 * A generic NBT deserializer.
 *
 * @param <T> Type of the deserialized value
 */
public interface Deserializer<T> {

	/**
	 * Deserialize NBT from an input stream.
	 *
	 * @param stream The input stream containing the NBT data
	 * @return The deserialized NBT
	 * @throws IOException In case there's an error reading from the input stream
	 */
	T fromStream(InputStream stream) throws IOException;

	/**
	 * Deserialize NBT from a file.
	 *
	 * @param file The file containing the NBT data
	 * @return The deserialized NBT
	 * @throws IOException In case there's an error reading from the file
	 */
	default T fromFile(File file) throws IOException {
		try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
			return fromStream(bis);
		}
	}

	/**
	 * Deserialize NBT from a byte array.
	 *
	 * @param data The byte array containing the NBT data
	 * @return The deserialized NBT
	 * @throws IOException In case there's an error reading from the byte array
	 */
	default T fromBytes(byte[] data) throws IOException {
		ByteArrayInputStream stream = new ByteArrayInputStream(data);
		return fromStream(stream);
	}

	/**
	 * Deserialize NBT from a JAR resource.
	 *
	 * @param clazz The class belonging to the JAR that contains the resource
	 * @param path The resource path containing the NBT data
	 * @return The deserialized NBT
	 * @throws IOException In case there's an error reading from the resource
	 */
	default T fromResource(Class<?> clazz, String path) throws IOException {
		try (InputStream stream = clazz.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				throw new IOException("resource \"" + path + "\" not found");
			}
			return fromStream(stream);
		}
	}

	/**
	 * Deserialize NBT from a URL.
	 *
	 * @param url The URL containing the NBT data
	 * @return The deserialized NBT
	 * @throws IOException In case there's an error reading from the URL
	 */
	default T fromURL(URL url) throws IOException {
		try (InputStream stream = url.openStream()) {
			return fromStream(stream);
		}
	}


}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\ExceptionBiFunction.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

/**
 * A bi-function that may throw an exception.
 *
 * @param <T> First value type
 * @param <U> Second value type
 * @param <R> Return type
 * @param <E> Exception type
 */
@FunctionalInterface
public interface ExceptionBiFunction<T, U, R, E extends Exception> {

	/**
	 * @param t The first value
	 * @param u The second value
	 * @return The return value
	 * @throws E The exception
	 */
	R accept(T t, U u) throws E;
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\ExceptionTriConsumer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

/**
 * A tri-consumer that may throw an exception.
 *
 * @param <T> First value type
 * @param <U> Second value type
 * @param <V> Third value type
 * @param <E> Exception type
 */
@FunctionalInterface
public interface ExceptionTriConsumer<T, U, V, E extends Exception> {

	/**
	 * @param t The first value
	 * @param u The second value
	 * @param v The third value
	 * @throws E The exception
	 */
	void accept(T t, U u, V v) throws E;
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\MaxDepthIO.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

public interface MaxDepthIO {

	default int decrementMaxDepth(int maxDepth) {
		if (maxDepth < 0) {
			throw new IllegalArgumentException("negative maximum depth is not allowed");
		} else if (maxDepth == 0) {
			throw new MaxDepthReachedException("reached maximum depth of NBT structure");
		}
		return --maxDepth;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\MaxDepthReachedException.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

/**
 * Exception indicating that the maximum (de-)serialization depth has been reached.
 */
public class MaxDepthReachedException extends RuntimeException {

	public MaxDepthReachedException(String msg) {
		super(msg);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\Serializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public interface Serializer<T> {

	void toStream(T object, OutputStream out) throws IOException;

	default void toFile(T object, File file) throws IOException {
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
			toStream(object, bos);
		}
	}

	default byte[] toBytes(T object) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		toStream(object, bos);
		bos.close();
		return bos.toByteArray();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\StringDeserializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

public interface StringDeserializer<T> extends Deserializer<T> {

	T fromReader(Reader reader) throws IOException;

	default T fromString(String s) throws IOException {
		return fromReader(new StringReader(s));
	}

	@Override
	default T fromStream(InputStream stream) throws IOException {
		try (Reader reader = new InputStreamReader(stream)) {
			return fromReader(reader);
		}
	}

	@Override
	default T fromFile(File file) throws IOException {
		try (Reader reader = new FileReader(file)) {
			return fromReader(reader);
		}
	}

	@Override
	default T fromBytes(byte[] data) throws IOException {
		return fromReader(new StringReader(new String(data)));
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\StringSerializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

public interface StringSerializer<T> extends Serializer<T> {

	void toWriter(T object, Writer writer) throws IOException;

	default String toString(T object) throws IOException {
		Writer writer = new StringWriter();
		toWriter(object, writer);
		writer.flush();
		return writer.toString();
	}

	@Override
	default void toStream(T object, OutputStream stream) throws IOException {
		Writer writer = new OutputStreamWriter(stream);
		toWriter(object, writer);
		writer.flush();
	}

	@Override
	default void toFile(T object, File file) throws IOException {
		try (Writer writer = new FileWriter(file)) {
			toWriter(object, writer);
		}
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\parser\LitematicaParser.java

```java
package cn.myfrank.stationbuilder.schematic4j.parser;

import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.NumberTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;
import cn.myfrank.stationbuilder.schematic4j.schematic.LitematicaSchematic.PendingTicks;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.LitematicaSchematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.LitematicaSchematic.Metadata;
import cn.myfrank.stationbuilder.schematic4j.schematic.LitematicaSchematic.Region;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.SIZED;
import static cn.myfrank.stationbuilder.schematic4j.utils.DateUtils.epochToDate;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompound;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompoundList;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getInt;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getLong;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getLongArray;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.unwrap;

/**
 * Parses Litematica files (<i>.litematic</i>).
 * <p>
 * Specification:<br>
 * - <a href="https://github.com/maruohon/litematica/issues/53#issuecomment-520279558">https://github.com/maruohon/litematica/issues/53#issuecomment-520279558</a>
 * - <a href="https://github.com/maruohon/litematica/blob/pre-rewrite/fabric/1.20.x/src/main/java/fi/dy/masa/litematica/schematic/LitematicaSchematic.java">https://github.com/maruohon/litematica/blob/pre-rewrite/fabric/1.20.x/src/main/java/fi/dy/masa/litematica/schematic/LitematicaSchematic.java</a>
 */
public class LitematicaParser implements Parser {

	private static final Logger log = LoggerFactory.getLogger(LitematicaParser.class);

	public static final String NBT_MINECRAFT_DATA_VERSION = "MinecraftDataVersion";
	public static final String NBT_VERSION = "Version";

	public static final String NBT_METADATA = "Metadata";
	public static final String NBT_METADATA_NAME = "Name";
	public static final String NBT_METADATA_DESCRIPTION = "Description";
	public static final String NBT_METADATA_AUTHOR = "Author";
	public static final String NBT_METADATA_TIME_CREATED = "TimeCreated";
	public static final String NBT_METADATA_TIME_MODIFIED = "TimeModified";
	public static final String NBT_METADATA_ENCLOSING_SIZE = "EnclosingSize";
	public static final String NBT_METADATA_REGION_COUNT = "RegionCount";
	public static final String NBT_METADATA_TOTAL_BLOCKS = "TotalBlocks";
	public static final String NBT_METADATA_TOTAL_VOLUME = "TotalVolume";
	public static final String NBT_METADATA_PREVIEW_IMAGE_DATA = "PreviewImageData";

	public static final String NBT_REGIONS = "Regions";
	public static final String NBT_REGION_POSITION = "Position";
	public static final String NBT_REGION_SIZE = "Size";
	public static final String NBT_REGION_BLOCK_STATES = "BlockStates";
	public static final String NBT_REGION_BLOCK_STATE_PALETTE = "BlockStatePalette";
	public static final String NBT_REGION_TILE_ENTITIES = "TileEntities";
	public static final String NBT_REGION_ENTITIES = "Entities";
	public static final String NBT_REGION_PENDING_BLOCK_TICKS = "PendingBlockTicks";
	public static final String NBT_REGION_PENDING_FLUID_TICKS = "PendingFluidTicks";

	@Override
	public @NotNull Schematic parse(@Nullable CompoundTag nbt) throws ParsingException {
		log.debug("Parsing Litematica schematic");

		final LitematicaSchematic schematic = new LitematicaSchematic();
		if (nbt == null) {
			return schematic;
		}

		parseVersion(nbt, schematic);
		parseMinecraftDataVersion(nbt, schematic);
		parseMetadata(nbt, schematic);
		parseRegions(nbt, schematic);

		return schematic;
	}

	protected void parseVersion(CompoundTag nbt, LitematicaSchematic schematic) {
		// Default to version 1 if none provided
		schematic.version = getInt(nbt, NBT_VERSION).orElse(1);
	}

	protected void parseMinecraftDataVersion(CompoundTag nbt, LitematicaSchematic schematic) {
		getInt(nbt, NBT_MINECRAFT_DATA_VERSION).ifPresent(dataVersion -> schematic.minecraftDataVersion = dataVersion);
	}

	protected void parseMetadata(CompoundTag nbt, LitematicaSchematic schematic) {
		final CompoundTag metadataTag = getCompound(nbt, NBT_METADATA).orElse(null);
		if (metadataTag == null) {
			return;
		}

		final Metadata metadata = new Metadata();

		for (final Map.Entry<String, Tag<?>> entry : metadataTag) {
			final String key = entry.getKey();
			final Tag<?> tag = entry.getValue();
			switch (key) {
				case NBT_METADATA_NAME:
					metadata.name = ((StringTag) tag).getValue();
					break;
				case NBT_METADATA_DESCRIPTION:
					metadata.description = ((StringTag) tag).getValue();
				case NBT_METADATA_AUTHOR:
					metadata.author = ((StringTag) tag).getValue();
					break;
				case NBT_METADATA_TIME_CREATED:
					long timeCreatedEpochMillis = ((NumberTag<?>) tag).asLong();
					metadata.timeCreated = epochToDate(timeCreatedEpochMillis);
					break;
				case NBT_METADATA_TIME_MODIFIED:
					long timeModifiedEpochMillis = ((NumberTag<?>) tag).asLong();
					metadata.timeModified = epochToDate(timeModifiedEpochMillis);
					break;
				case NBT_METADATA_ENCLOSING_SIZE:
					final SchematicBlockPos pos = SchematicBlockPos.from(tag);
					if (pos != null) {
						metadata.enclosingSize = pos;
					}
					break;
				case NBT_METADATA_REGION_COUNT:
					metadata.regionCount = ((NumberTag<?>) tag).asInt();
					break;
				case NBT_METADATA_TOTAL_BLOCKS:
					metadata.totalBlocks = ((NumberTag<?>) tag).asLong();
					break;
				case NBT_METADATA_TOTAL_VOLUME:
					metadata.totalVolume = ((NumberTag<?>) tag).asLong();
					break;
				case NBT_METADATA_PREVIEW_IMAGE_DATA:
					metadata.previewImageData = ((IntArrayTag) tag).getValue();
				default:
					metadata.extra.put(key, unwrap(tag));
			}
		}

		schematic.metadata = metadata;
	}

	protected void parseRegions(CompoundTag nbt, LitematicaSchematic schematic) {
		final CompoundTag regionsTag = nbt.getCompoundTag(NBT_REGIONS);
		if (regionsTag == null) {
			return;
		}

		final Region[] regions = new Region[regionsTag.size()];

		int i = 0;
		for (final Map.Entry<String, Tag<?>> entry : regionsTag) {
			final String regionName = entry.getKey();
			final Tag<?> regionTag = entry.getValue();
			if (regionTag instanceof CompoundTag) {
				final Region region = parseRegion(((CompoundTag) regionTag), regionName);
				regions[i] = region;
			} else {
				log.warn("Invalid region found; expected a compound NBT tag but got {}", regionTag != null ? regionTag.getClass().getName() : null);
			}
			i++;
		}

		schematic.regions = regions;
	}

	protected Region parseRegion(CompoundTag regionTag, String regionName) {
		final Region region = new Region();
		region.name = regionName;

		final SchematicBlockPos position = SchematicBlockPos.from(regionTag.getCompoundTag(NBT_REGION_POSITION));
		if (position != null) {
			region.position = position;
		}

		final SchematicBlockPos size = SchematicBlockPos.from(regionTag.getCompoundTag(NBT_REGION_SIZE));
		if (size != null) {
			region.size = size;
		}

		parseBlocks(regionTag, region);
		parseBlockEntities(regionTag, region);
		parseEntities(regionTag, region);
		parsePendingBlockTicks(regionTag, region);
		parsePendingFluidTicks(regionTag, region);

		return region;
	}

	protected void parseBlocks(CompoundTag regionTag, Region region) {
		final ListTag<CompoundTag> paletteTag = getCompoundList(regionTag, NBT_REGION_BLOCK_STATE_PALETTE).orElse(null);
		if (paletteTag != null) {
			final Spliterator<CompoundTag> paletteSpliterator = Spliterators.spliterator(paletteTag.iterator(), paletteTag.size(), DISTINCT | SIZED | NONNULL | IMMUTABLE);
			region.blockStatePalette = StreamSupport.stream(paletteSpliterator, false)
					.map(LitematicaParser::readBlockPaletteEntry)
					.toArray(SchematicBlock[]::new);
		}

		final long[] packedBlockStates = getLongArray(regionTag, NBT_REGION_BLOCK_STATES).orElse(null);
		if (packedBlockStates != null) {
			final SchematicBlockPos regionSize = getRegionSize(region);
			final int totalVolume = regionSize.x * regionSize.y * regionSize.z;
			final int bitsPerEntry = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(region.blockStatePalette.length - 1));
			final long maxEntryValue = (1L << bitsPerEntry) - 1L;

			final int[] blockStates = new int[totalVolume];

			for (int unpackedIdx = 0; unpackedIdx < totalVolume; unpackedIdx++) {
				long startOffset = (long) unpackedIdx * bitsPerEntry;
				int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
				int endArrIndex = (int) (((unpackedIdx + 1L) * (long) bitsPerEntry - 1L) >> 6);
				int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

				final int value;
				if (startArrIndex == endArrIndex) {
					value = (int) (packedBlockStates[startArrIndex] >>> startBitOffset & maxEntryValue);
				} else {
					int endOffset = 64 - startBitOffset;
					value = (int) ((packedBlockStates[startArrIndex] >>> startBitOffset | packedBlockStates[endArrIndex] << endOffset) & maxEntryValue);
				}

				blockStates[unpackedIdx] = value;
			}

			region.blockStates = blockStates;
		}
	}

	@SuppressWarnings("unchecked")
	protected void parseBlockEntities(CompoundTag regionTag, Region region) {
		final ListTag<CompoundTag> blockEntitiesTag = getCompoundList(regionTag, NBT_REGION_TILE_ENTITIES).orElse(null);
		if (blockEntitiesTag == null) {
			return;
		}

		final SchematicBlockEntity[] blockEntities = new SchematicBlockEntity[blockEntitiesTag.size()];

		int i = 0;
		for (final Tag<?> blockEntityTag : blockEntitiesTag) {
			final SchematicBlockEntity blockEntity = SchematicBlockEntity.fromNbt(blockEntityTag);
			if (blockEntity != null) {
				final Object blockEntityNbtData = blockEntity.data.get("TileNBT");
				if (blockEntityNbtData instanceof Map<?, ?>) {
					blockEntity.data.remove("TileNBT");
					blockEntity.data.putAll(((Map<String, ?>) blockEntityNbtData));
				}
			}

			blockEntities[i++] = blockEntity;
		}

		region.blockEntities = blockEntities;
	}

	@SuppressWarnings("unchecked")
	protected void parseEntities(CompoundTag regionTag, Region region) {
		final ListTag<CompoundTag> entitiesTag = getCompoundList(regionTag, NBT_REGION_ENTITIES).orElse(null);
		if (entitiesTag == null) {
			return;
		}

		final SchematicEntity[] entities = new SchematicEntity[entitiesTag.size()];

		int i = 0;
		for (final Tag<?> entityTag : entitiesTag) {
			final SchematicEntity entity = SchematicEntity.fromNbt(entityTag);
			if (entity != null) {
				final Object entityNbtData = entity.data.get("EntityData");
				if (entityNbtData instanceof Map<?, ?>) {
					entity.data.remove("EntityData");
					entity.data.putAll(((Map<String, ?>) entityNbtData));
				}
			}

			entities[i++] = entity;
		}

		region.entities = entities;
	}

	protected void parsePendingBlockTicks(CompoundTag regionTag, Region region) {
		final ListTag<CompoundTag> pendingTicksTag = getCompoundList(regionTag, NBT_REGION_PENDING_BLOCK_TICKS).orElse(null);
		region.pendingBlockTicks = readPendingTicks(pendingTicksTag);
	}

	protected void parsePendingFluidTicks(CompoundTag regionTag, Region region) {
		final ListTag<CompoundTag> pendingTicksTag = getCompoundList(regionTag, NBT_REGION_PENDING_FLUID_TICKS).orElse(null);
		region.pendingBlockTicks = readPendingTicks(pendingTicksTag);
	}

	public static @Nullable SchematicBlock readBlockPaletteEntry(CompoundTag nbt) {
		if (nbt == null) {
			return null;
		}

		final String name = nbt.getString("Name");
		final Map<String, String> states = new TreeMap<>();

		final CompoundTag propertiesTag = nbt.getCompoundTag("Properties");
		if (propertiesTag != null) {
			for (final Map.Entry<String, Tag<?>> entry : propertiesTag) {
				states.put(entry.getKey(), unwrap(entry.getValue()).toString());
			}
		}

		return new SchematicBlock(name, states);
	}

	public static @NotNull SchematicBlockPos getRegionSize(Region region) {
		int posEndRelX = region.size.x;
		int posEndRelY = region.size.y;
		int posEndRelZ = region.size.z;
		posEndRelX = posEndRelX >= 0 ? posEndRelX - 1 : posEndRelX + 1;
		posEndRelY = posEndRelY >= 0 ? posEndRelY - 1 : posEndRelY + 1;
		posEndRelZ = posEndRelZ >= 0 ? posEndRelZ - 1 : posEndRelZ + 1;

		posEndRelX += region.position.x;
		posEndRelY += region.position.y;
		posEndRelZ += region.position.z;

		int posMinX = Math.min(region.position.x, posEndRelX);
		int posMinY = Math.min(region.position.y, posEndRelY);
		int posMinZ = Math.min(region.position.z, posEndRelZ);

		int posMaxX = Math.max(region.position.x, posEndRelX);
		int posMaxY = Math.max(region.position.y, posEndRelY);
		int posMaxZ = Math.max(region.position.z, posEndRelZ);

		return new SchematicBlockPos(posMaxX - posMinX + 1, posMaxY - posMinY + 1, posMaxZ - posMinZ + 1);
	}

	@NotNull
	private static PendingTicks[] readPendingTicks(ListTag<CompoundTag> pendingTicksListTag) {
		if (pendingTicksListTag == null) {
			return new PendingTicks[0];
		}

		final PendingTicks[] pendingTicks = new PendingTicks[pendingTicksListTag.size()];

		int i = 0;
		for (final CompoundTag pendingTicksTag : pendingTicksListTag) {
			final PendingTicks pendingTick = new PendingTicks();
			pendingTick.priority = getInt(pendingTicksTag, "Priority").orElse(null);
			pendingTick.subTick = getLong(pendingTicksTag, "SubTick").orElse(null);
			pendingTick.time = getInt(pendingTicksTag, "Time").orElse(null);
			pendingTick.x = getInt(pendingTicksTag, "x").orElse(null);
			pendingTick.y = getInt(pendingTicksTag, "y").orElse(null);
			pendingTick.z = getInt(pendingTicksTag, "z").orElse(null);

			pendingTicks[i++] = pendingTick;
		}
		return pendingTicks;
	}

	@Override
	public String toString() {
		return "LitematicaParser";
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\parser\Parser.java

```java
package cn.myfrank.stationbuilder.schematic4j.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;

/**
 * A schematic parser.
 */
public interface Parser {

	/**
	 * Parses the input NBT into a schematic.
	 *
	 * @param nbt The input NBT.
	 * @return The parsed schematic.
	 * @throws ParsingException In case there is a parsing error
	 */
	@NotNull
	Schematic parse(@Nullable CompoundTag nbt) throws ParsingException;
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\parser\SchematicaParser.java

```java
package cn.myfrank.stationbuilder.schematic4j.parser;

import java.util.Map.Entry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.NumberTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.SchematicaSchematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicItem;

import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getByte;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getByteArrayOrThrow;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompound;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompoundList;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompoundOrThrow;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getShort;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getString;

/**
 * Parses Schematica files (<i>.schematic</i>).
 * <p>
 * Specification:<br>
 * - <a href="https://minecraft.fandom.com/wiki/Schematic_file_format">https://minecraft.fandom.com/wiki/Schematic_file_format</a>
 * - <a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java</a>
 */
public class SchematicaParser implements Parser {

	private static final Logger log = LoggerFactory.getLogger(SchematicaParser.class);

	public static final String NBT_MATERIALS = "Materials";

	public static final String NBT_ICON = "Icon";
	public static final String NBT_ICON_ID = "id";
	public static final String NBT_ICON_COUNT = "Count";
	public static final String NBT_ICON_DAMAGE = "Damage";
	public static final String NBT_BLOCKS = "Blocks";
	public static final String NBT_DATA = "Data";
	public static final String NBT_ADD_BLOCKS = "AddBlocks";
	public static final String NBT_ADD_BLOCKS_SCHEMATICA = "Add";
	public static final String NBT_WIDTH = "Width";
	public static final String NBT_LENGTH = "Length";
	public static final String NBT_HEIGHT = "Height";
	public static final String NBT_MAPPING_SCHEMATICA = "SchematicaMapping";
	public static final String NBT_TILE_ENTITIES = "TileEntities";
	public static final String NBT_ENTITIES = "Entities";

	@Override
	public @NotNull Schematic parse(@Nullable CompoundTag nbt) throws ParsingException {
		log.debug("Parsing Schematica schematic");

		final SchematicaSchematic schematic = new SchematicaSchematic();
		if (nbt == null) {
			return schematic;
		}

		parseIcon(nbt, schematic);
		parseBlocks(nbt, schematic);
		parseBlockEntities(nbt, schematic);
		parseEntities(nbt, schematic);
		parseMaterials(nbt, schematic);

		return schematic;
	}

	private void parseIcon(CompoundTag root, SchematicaSchematic schematic) {
		log.trace("Parsing icon");
		getCompound(root, NBT_ICON).ifPresent(iconTag -> {
			schematic.icon = new SchematicItem(
					getString(iconTag, NBT_ICON_ID).orElse("minecraft:dirt"),
					getByte(iconTag, NBT_ICON_COUNT).orElse((byte) 1),
					getShort(iconTag, NBT_ICON_DAMAGE).orElse((short) 0)
			);
		});
	}

	private void parseBlocks(CompoundTag root, SchematicaSchematic schematic) throws ParsingException {
		log.trace("Parsing blocks");

		Tag<?> wTag = root.get(NBT_WIDTH);
		if (wTag instanceof NumberTag) schematic.width = ((NumberTag<?>) wTag).asInt();

		Tag<?> hTag = root.get(NBT_HEIGHT);
		if (hTag instanceof NumberTag) schematic.height = ((NumberTag<?>) hTag).asInt();

		Tag<?> lTag = root.get(NBT_LENGTH);
		if (lTag instanceof NumberTag) schematic.length = ((NumberTag<?>) lTag).asInt();

		/* Mappings */
		final CompoundTag paletteTag = getCompoundOrThrow(root, NBT_MAPPING_SCHEMATICA);
		final int biggestId = paletteTag.values().stream().mapToInt(tag -> tag instanceof NumberTag ? ((NumberTag<?>) tag).asInt() : 0).max().orElse(0) + 1;
		log.trace("Palette size: {}, biggest ID: {}", paletteTag.size(), biggestId);
		final String[] palette = new String[biggestId];
		for (Entry<String, Tag<?>> entry : paletteTag) {
			final String blockName = entry.getKey();
			final int index = ((NumberTag<?>) entry.getValue()).asInt();
			palette[index] = blockName;
		}

		// Load the (optional) palette
		final byte[] blocksRaw = getByteArrayOrThrow(root, NBT_BLOCKS);
		final byte[] blockDataRaw = getByteArrayOrThrow(root, NBT_DATA);

		boolean extra = false;
		byte[] extraBlocks = null;
		if (root.containsKey(NBT_ADD_BLOCKS)) {
			extra = true;
			byte[] extraBlocksNibble = getByteArrayOrThrow(root, NBT_ADD_BLOCKS);
			extraBlocks = new byte[extraBlocksNibble.length * 2];
			for (int i = 0; i < extraBlocksNibble.length; i++) {
				extraBlocks[i * 2] = (byte) ((extraBlocksNibble[i] >> 4) & 0xF);
				extraBlocks[i * 2 + 1] = (byte) (extraBlocksNibble[i] & 0xF);
			}
		} else if (root.containsKey(NBT_ADD_BLOCKS_SCHEMATICA)) {
			extra = true;
			extraBlocks = getByteArrayOrThrow(root, NBT_ADD_BLOCKS_SCHEMATICA);
		}

		int totalVolume = blocksRaw.length;
		int expectedTotalVolume = schematic.width * schematic.height * schematic.length;
		if (totalVolume != expectedTotalVolume) {
			log.warn("Number of blocks does not match expected. Expected {} blocks, but got {}", expectedTotalVolume, totalVolume);
		}

		int[] blocks = new int[totalVolume];
		int[] blockMetadata = new int[totalVolume];

		for (int index = 0; index < totalVolume; index++) {
			final int blockId = (blocksRaw[index] & 0xFF) | (extra ? ((extraBlocks[index] & 0xFF) << 8) : 0);
			final int metadata = blockDataRaw[index] & 0xFF;

			blocks[index] = blockId;
			blockMetadata[index] = metadata;
		}

		schematic.blockIds = blocks;
		schematic.blockMetadata = blockMetadata;
		schematic.blockPalette = palette;
		log.debug("Loaded {} blocks", blocks.length);
	}

	private void parseBlockEntities(CompoundTag root, SchematicaSchematic schematic) {
		final ListTag<CompoundTag> blockEntitiesTag = getCompoundList(root, NBT_TILE_ENTITIES).orElse(null);
		if (blockEntitiesTag == null) {
			log.trace("No block entities found");
			return;
		}

		log.trace("Parsing block entities");
		final SchematicBlockEntity[] blockEntities = new SchematicBlockEntity[blockEntitiesTag.size()];

		int i = 0;
		for (CompoundTag blockEntityTag : blockEntitiesTag) {
			final SchematicBlockEntity blockEntity = SchematicBlockEntity.fromNbt(blockEntityTag);
			blockEntities[i++] = blockEntity;
		}

		schematic.blockEntities = blockEntities;
		log.debug("Loaded {} block entities", blockEntities.length);
	}

	private void parseEntities(CompoundTag root, SchematicaSchematic schematic) {
		final ListTag<CompoundTag> entitiesTag = getCompoundList(root, NBT_ENTITIES).orElse(null);
		if (entitiesTag == null) {
			log.trace("No entities found");
			return;
		}

		log.trace("Parsing entities");
		final SchematicEntity[] entities = new SchematicEntity[entitiesTag.size()];

		int i = 0;
		for (final CompoundTag entityTag : entitiesTag) {
			final SchematicEntity entity = SchematicEntity.fromNbt(entityTag);
			entities[i++] = entity;
		}

		schematic.entities = entities;
		log.debug("Loaded {} entities", entities.length);
	}

	private void parseMaterials(CompoundTag root, SchematicaSchematic schematic) {
		log.trace("Parsing materials");
		getString(root, NBT_MATERIALS).ifPresent(materials -> schematic.materials = materials);
	}

	@Override
	public String toString() {
		return "SchematicaParser";
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\parser\SpongeParser.java

```java
package cn.myfrank.stationbuilder.schematic4j.parser;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.NumberTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.SpongeSchematic;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBiome;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static java.util.stream.Collectors.toMap;
import static cn.myfrank.stationbuilder.schematic4j.utils.DateUtils.epochToDate;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getByteArray;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompound;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getCompoundList;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getInt;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getIntArray;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getShort;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.unwrap;

/**
 * Parses Sponge Schematic files (<i>.schem</i>).
 * <p>
 * The SCHEM format replaced the .SCHEMATIC format in versions 1.13+ of Minecraft Java Edition.
 *
 * <h2>Specification</h2>
 * <ul>
 *     <li><a href="https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-1.md">Version 1</a></li>
 *     <li><a href="https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md">Version 2</a></li>
 *     <li><a href="https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md">Version 3</a></li>
 * </ul>
 */
public class SpongeParser implements Parser {

	public static final String NBT_VERSION = "Version";
	public static final String NBT_DATA_VERSION = "DataVersion";
	public static final String NBT_METADATA = "Metadata";
	public static final String NBT_METADATA_NAME = "Name";
	public static final String NBT_METADATA_AUTHOR = "Author";
	public static final String NBT_METADATA_DATE = "Date";
	public static final String NBT_METADATA_REQUIRED_MODS = "RequiredMods";
	public static final String NBT_WIDTH = "Width";
	public static final String NBT_HEIGHT = "Height";
	public static final String NBT_LENGTH = "Length";
	public static final String NBT_OFFSET = "Offset";
	public static final String NBT_PALETTE = "Palette";
	public static final String NBT_PALETTE_MAX = "PaletteMax";
	public static final String NBT_BLOCK_DATA = "BlockData";
	public static final String NBT_BLOCK_ENTITIES = "BlockEntities";
	public static final String NBT_BLOCK_ENTITIES_ID = "Id";
	public static final String NBT_BLOCK_ENTITIES_POS = "Pos";
	public static final String NBT_TILE_ENTITIES = "TileEntities";
	public static final String NBT_BIOME_PALETTE = "BiomePalette";
	public static final String NBT_BIOME_PALETTE_MAX = "BiomePaletteMax";
	public static final String NBT_BIOME_DATA = "BiomeData";
	public static final String NBT_ENTITIES = "Entities";
	public static final String NBT_ENTITIES_ID = "Id";
	public static final String NBT_ENTITIES_POS = "Pos";
	public static final String NBT_ENTITIES_EXTRA = "Extra";
	public static final String NBT_V3_BLOCKS = "Blocks";
	public static final String NBT_V3_BIOMES = "Biomes";
	public static final String NBT_V3_DATA = "Data";

	private static final Logger log = LoggerFactory.getLogger(SpongeParser.class);

	@Override
	public @NotNull Schematic parse(@Nullable CompoundTag nbt) throws ParsingException {
		log.debug("Parsing Sponge schematic");

		final SpongeSchematic schematic = new SpongeSchematic();
		if (nbt == null) {
			return schematic;
		}

		parseVersion(nbt, schematic);
		parseDataVersion(nbt, schematic);
		parseMetadata(nbt, schematic);
		parseOffset(nbt, schematic);
		parseBlocks(nbt, schematic);
		parseBlockEntities(nbt, schematic);
		parseEntities(nbt, schematic);
		parseBiomes(nbt, schematic);

		return schematic;
	}

	protected void parseVersion(CompoundTag rootTag, SpongeSchematic schematic) {
		// Default to version 1 if none provided
		schematic.version = getInt(rootTag, NBT_VERSION).orElse(1);
		if (schematic.version > 3) {
			log.warn("Sponge Schematic version {} is not officially supported. Use at your own risk", schematic.version);
		}
	}

	protected void parseDataVersion(CompoundTag root, SpongeSchematic schematic) {
		log.trace("Parsing data version");

		// Data Version is optional for v1, but required for v2 and v3
		getInt(root, NBT_DATA_VERSION).ifPresent(dataVersion -> schematic.dataVersion = dataVersion);
	}

	protected void parseMetadata(CompoundTag root, SpongeSchematic schematic) {
		final CompoundTag metadataTag = getCompound(root, NBT_METADATA).orElse(null);
		if (metadataTag == null) {
			log.debug("No metadata found");
			return;
		}

		log.trace("Parsing metadata");
		for (final Entry<String, Tag<?>> entry : metadataTag) {
			final String key = entry.getKey();
			final Tag<?> tag = entry.getValue();
			switch (key) {
				case NBT_METADATA_NAME:
					schematic.metadata.name = ((StringTag) tag).getValue();
					break;
				case NBT_METADATA_AUTHOR:
					schematic.metadata.author = ((StringTag) tag).getValue();
					break;
				case NBT_METADATA_DATE:
					long dateEpochMillis = ((NumberTag<?>) tag).asLong();
					schematic.metadata.date = epochToDate(dateEpochMillis);
					break;
				case NBT_METADATA_REQUIRED_MODS:
					final ListTag<StringTag> stringTags = ((ListTag<?>) tag).asStringTagList();
					schematic.metadata.requiredMods = StreamSupport.stream(stringTags.spliterator(), false)
							.map(StringTag::getValue)
							.toArray(String[]::new);
					break;
				default:
					schematic.metadata.extra.put(key, unwrap(tag));
			}
		}
	}

	protected void parseOffset(CompoundTag root, SpongeSchematic schematic) {
		log.trace("Parsing offset");
		getIntArray(root, NBT_OFFSET).ifPresent(offset -> {
			schematic.offset = SchematicBlockPos.from(offset);
			log.debug("Loaded offset is {}", schematic.offset);
		});
	}

	protected void parseBlocks(CompoundTag root, SpongeSchematic schematic) {
		log.trace("Parsing blocks");

		Tag<?> wTag = root.get(NBT_WIDTH);
		if (wTag instanceof NumberTag) schematic.width = ((NumberTag<?>) wTag).asInt();

		Tag<?> hTag = root.get(NBT_HEIGHT);
		if (hTag instanceof NumberTag) schematic.height = ((NumberTag<?>) hTag).asInt();

		Tag<?> lTag = root.get(NBT_LENGTH);
		if (lTag instanceof NumberTag) schematic.length = ((NumberTag<?>) lTag).asInt();

		final CompoundTag blocksTag = getBlocksTag(root, schematic.version);
		if (blocksTag == null) {
			log.debug("No block container");
			return;
		}

		// Load the block palette
		getCompound(blocksTag, NBT_PALETTE).ifPresent(blockPaletteTag -> {
			log.trace("Block palette size: {}", blockPaletteTag.size());

			final int paletteMax = blocksTag.getInt(NBT_PALETTE_MAX);
			if (paletteMax > 0 && blockPaletteTag.size() != paletteMax) {
				log.warn("Palette actual size does not match expected size. Expected {} but got {}", paletteMax, blockPaletteTag.size());
			}

			final SchematicBlock[] blockPalette = new SchematicBlock[blockPaletteTag.size()];
			for (Entry<String, Tag<?>> entry : blockPaletteTag) {
				final SchematicBlock block = new SchematicBlock(entry.getKey());
				final int index = ((IntTag) entry.getValue()).asInt();
				blockPalette[index] = block;
			}

			schematic.blockPalette = blockPalette;
		});

		// Load the block data
		getByteArray(blocksTag, schematic.version >= 3 ? NBT_V3_DATA : NBT_BLOCK_DATA).ifPresent(blockDataRaw -> {
			int[] blockData = new int[schematic.width * schematic.height * schematic.length];

			// --- Uses code from https://github.com/SpongePowered/Sponge/blob/aa2c8c53b4f9f40297e6a4ee281bee4f4ce7707b/src/main/java/org/spongepowered/common/data/persistence/SchematicTranslator.java#L147-L175
			int index = 0;
			int i = 0;
			while (i < blockDataRaw.length) {
				int value = 0;
				int varintLength = 0;
				while (true) {
					value |= (blockDataRaw[i] & 127) << (varintLength++ * 7);
					if (varintLength > 5) {
						log.warn("VarInt for block index is too big; probably corrupted data");
						continue;
					}
					if ((blockDataRaw[i] & 128) != 128) {
						i++;
						break;
					}
					i++;
				}

				blockData[index] = value;
				index++;
			}
			// ---

			schematic.blocks = blockData;
			log.debug("Loaded {} blocks", blockData.length);
		});

	}

	protected static CompoundTag getBlocksTag(CompoundTag root, int version) {
		if (version >= 3) {
			return root.getCompoundTag(NBT_V3_BLOCKS);
		} else {
			return root;
		}
	}

	protected void parseBlockEntities(CompoundTag root, SpongeSchematic schematic) {
		final CompoundTag blocksTag = getBlocksTag(root, schematic.version);
		final String blockEntitiesTagName = schematic.version == 1 ? NBT_TILE_ENTITIES : NBT_BLOCK_ENTITIES;
		final Optional<ListTag<CompoundTag>> blockEntitiesListTag = getCompoundList(blocksTag, blockEntitiesTagName);

		if (!blockEntitiesListTag.isPresent()) {
			log.trace("No block entities found");
			return;
		}

		log.trace("Parsing block entities");
		final ListTag<CompoundTag> blockEntitiesTag = blockEntitiesListTag.get();
		final SchematicBlockEntity[] blockEntities = new SchematicBlockEntity[blockEntitiesTag.size()];

		int i = 0;
		for (CompoundTag blockEntityTag : blockEntitiesTag) {
			final String id = blockEntityTag.getString(NBT_BLOCK_ENTITIES_ID);
			final SchematicBlockPos pos = getIntArray(blockEntityTag, NBT_BLOCK_ENTITIES_POS)
					.map(SchematicBlockPos::from)
					.orElse(SchematicBlockPos.ZERO);
			final Map<String, Object> data = blockEntityTag.entrySet().stream()
					.filter(tag -> !tag.getKey().equals(NBT_ENTITIES_ID) && !tag.getKey().equals(NBT_ENTITIES_POS))
					.collect(toMap(Entry::getKey, e -> unwrap(e.getValue()), (a, b) -> b, TreeMap::new));

			blockEntities[i] = new SchematicBlockEntity(id, pos, data);
			i++;
		}

		schematic.blockEntities = blockEntities;
		log.debug("Loaded {} block entities", blockEntities.length);
	}

	@SuppressWarnings("unchecked")
	protected void parseEntities(CompoundTag root, SpongeSchematic schematic) {
		final ListTag<CompoundTag> entitiesTag = getCompoundList(root, NBT_ENTITIES).orElse(null);
		if (entitiesTag == null) {
			log.trace("No entities found");
			return;
		}

		log.trace("Parsing entities");
		final SchematicEntity[] entities = new SchematicEntity[entitiesTag.size()];

		int i = 0;
		for (CompoundTag entityTag : entitiesTag) {
			final SchematicEntity entity = SchematicEntity.fromNbt(entityTag);
			if (entity != null) {
				// Entity NBT stored in v2
				final Object entityExtra = entity.data.get(NBT_ENTITIES_EXTRA);
				if (entityExtra instanceof Map<?, ?>) {
					entity.data.remove(NBT_ENTITIES_EXTRA);
					entity.data.putAll(((Map<String, ?>) entityExtra));
				}

				// Entity NBT stored in v3
				final Object entityData = entity.data.get(NBT_V3_DATA);
				if (entityData instanceof Map<?, ?>) {
					entity.data.remove(NBT_V3_DATA);
					entity.data.putAll(((Map<String, ?>) entityData));
				}
			}

			entities[i++] = entity;
		}

		schematic.entities = entities;
		log.debug("Loaded {} entities", entities.length);
	}

	protected void parseBiomes(CompoundTag root, SpongeSchematic schematic) {
		log.trace("Parsing biomes");

		final int version = schematic.version;

		final CompoundTag biomesTag = getBiomesTag(root, version);
		if (biomesTag == null) {
			log.trace("Did not have biome data");
			return;
		}

		// Load the palette
		getCompound(biomesTag, version >= 3 ? NBT_PALETTE : NBT_BIOME_PALETTE).ifPresent(biomePaletteTag -> {
			log.trace("Biome palette size: {}", biomePaletteTag.size());

			final int paletteMax = biomesTag.getInt(NBT_BIOME_PALETTE_MAX);
			if (paletteMax > 0 && biomePaletteTag.size() != paletteMax) {
				log.warn("Biome palette actual size does not match expected size. Expected {} but got {}", paletteMax, biomePaletteTag.size());
			}

			final SchematicBiome[] biomePalette = new SchematicBiome[biomePaletteTag.size()];
			for (Entry<String, Tag<?>> entry : biomePaletteTag) {
				final SchematicBiome biome = new SchematicBiome(entry.getKey());
				final int index = ((IntTag) entry.getValue()).asInt();
				biomePalette[index] = biome;
			}

			schematic.biomePalette = biomePalette;
		});

		// Load the biome data
		getByteArray(biomesTag, version >= 3 ? NBT_V3_DATA : NBT_BIOME_DATA).ifPresent(biomeDataRaw -> {
			final int biomeWidth = schematic.width;
			final int biomeHeight = version >= 3 ? schematic.height : 1;
			final int biomeLength = schematic.length;

			int[] biomeData = new int[biomeWidth * biomeHeight * biomeLength];

			int index = 0;
			int i = 0;
			while (i < biomeDataRaw.length) {
				int value = 0;
				int varintLength = 0;
				while (true) {
					value |= (biomeDataRaw[i] & 127) << (varintLength++ * 7);
					if (varintLength > 5) {
						log.warn("VarInt for biome index is too big; probably corrupted data");
						continue;
					}
					if ((biomeDataRaw[i] & 128) != 128) {
						i++;
						break;
					}
					i++;
				}

				biomeData[index] = value;
				index++;
			}

			schematic.biomes = biomeData;
			log.debug("Loaded {} biomes", biomeWidth * biomeLength);
		});
	}

	protected static CompoundTag getBiomesTag(CompoundTag root, int version) {
		if (version >= 3) {
			return root.getCompoundTag(NBT_V3_BIOMES);
		} else {
			return root;
		}
	}

	@Override
	public String toString() {
		return "SpongeSchematicParser";
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\LitematicaSchematic.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.Pair;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Litematica schematic. Read more about it at
 * <a href="https://github.com/maruohon/litematica/issues/53#issuecomment-520279558">https://github.com/maruohon/litematica/issues/53#issuecomment-520279558</a>.
 */
public class LitematicaSchematic implements Schematic {

	/**
	 * The schematic format version being used.
	 **/
	public int version = 1;

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 */
	public @Nullable Integer minecraftDataVersion;

	/**
	 * The optional metadata about the schematic.
	 */
	public @NotNull Metadata metadata = new Metadata();

	/**
	 * The regions that compose this schematic. They can be thought of as their own little schematics.
	 */
	public Region @NotNull [] regions = new Region[0];

	/**
	 * A Litematica schematic.
	 */
	public LitematicaSchematic() {
	}

	@Override
	public @NotNull SchematicFormat format() {
		return SchematicFormat.LITEMATICA;
	}

	@Override
	public int width() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.x : 0;
	}

	@Override
	public int height() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.y : 0;
	}

	@Override
	public int length() {
		return metadata.enclosingSize != null ? metadata.enclosingSize.z : 0;
	}

	@Override
	public @NotNull SchematicBlockPos offset() {
		return SchematicBlockPos.ZERO;
	}

	@Override
	public @NotNull SchematicBlock block(int x, int y, int z) {
		for (Region region : regions) {
			if ((region.position.x <= x && region.position.x + region.size.x > x)
					&& (region.position.y <= y && region.position.y + region.size.y > y)
					&& (region.position.z <= z && region.position.z + region.size.z > z)) {

				final int blockStateIndex = region.posToIndex(x, y, z);
				final int paletteIndex = region.blockStates[blockStateIndex];
				return region.blockStatePalette[paletteIndex];
			}
		}

		return AIR; // outside bounds
	}

	@Override
	public @NotNull Stream<Pair<SchematicBlockPos, SchematicBlock>> blocks() {
		return Arrays.stream(regions).flatMap(region -> IntStream.range(0, region.blockStates.length).mapToObj(idx -> {
			final SchematicBlockPos pos = region.indexToPos(idx);
			final int paletteIdx = region.blockStates[idx];
			final SchematicBlock block = region.blockStatePalette[paletteIdx];
			return new Pair<>(pos, block);
		}));
	}

	@Override
	public @NotNull Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(regions).flatMap(r -> Arrays.stream(r.blockEntities));
	}

	@Override
	public @NotNull Stream<SchematicEntity> entities() {
		return Arrays.stream(regions).flatMap(r -> Arrays.stream(r.entities));
	}

	/**
	 * The regions that compose this schematic. They can be thought of as their own little schematics.
	 *
	 * @return The regions
	 */
	public @NotNull Region[] regions() {
		return regions;
	}

	@Override
	public @Nullable String name() {
		return metadata.name;
	}

	@Override
	public @Nullable String author() {
		return metadata.author;
	}

	@Override
	public @Nullable LocalDateTime date() {
		return metadata.timeCreated;
	}

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 *
	 * @return The Minecraft data version
	 */
	public @Nullable Integer dataVersion() {
		return minecraftDataVersion;
	}

	/**
	 * The optional metadata about the schematic.
	 *
	 * @return The schematic metadata
	 */
	public @NotNull Metadata metadata() {
		return metadata;
	}

	@Override
	public String toString() {
		return "SchematicLitematica[" +
				"name=" + name() +
				", version=" + version +
				", dataVersion=" + minecraftDataVersion +
				", metadata=" + metadata +
				", regions=" + Arrays.toString(regions) +
				']';
	}

	/**
	 * The schematic metadata.
	 */
	public static class Metadata {
		/**
		 * The name of the schematic.
		 */
		public @Nullable String name;

		/**
		 * The description of the schematic.
		 */
		public @Nullable String description;

		/**
		 * The name of the author of the schematic.
		 */
		public @Nullable String author;

		/**
		 * The date that this schematic was created on.
		 */
		public @Nullable LocalDateTime timeCreated;

		/**
		 * The date that this schematic was modified on.
		 */
		public @Nullable LocalDateTime timeModified;

		/**
		 * The size of the schematic including all regions.
		 */
		public @Nullable SchematicBlockPos enclosingSize;

		/**
		 * The number of regions inside this schematic.
		 */
		public @Nullable Integer regionCount;

		/**
		 * The total number of blocks from all the regions that compose this schematic. Does not include air blocks.
		 */
		public @Nullable Long totalBlocks;

		/**
		 * The total volume of blocks from all the regions that compose this schematic. This includes air blocks.
		 */
		public @Nullable Long totalVolume;

		/**
		 * Schematic thumbnail, if available.
		 */
		public int @Nullable [] previewImageData;

		/**
		 * Extra metadata not represented in the specification.
		 */
		public @NotNull Map<String, Object> extra = new TreeMap<>();

		public Metadata() {
		}

		@Override
		public String toString() {
			return "Metadata[" +
					"name='" + name + '\'' +
					", author='" + author + '\'' +
					", timeCreated=" + timeCreated +
					", timeModified=" + timeModified +
					']';
		}
	}

	/**
	 * A schematic region.
	 * <p>
	 * It can be thought of as its own little schematics, since it contains a unique size and block palette.
	 */
	public static class Region {
		/**
		 * The region name.
		 */
		public @Nullable String name;

		/**
		 * The region position in reference to the schematic origin at (0, 0, 0).
		 */
		public @NotNull SchematicBlockPos position = SchematicBlockPos.ZERO;

		/**
		 * The region size.
		 */
		public @NotNull SchematicBlockPos size = SchematicBlockPos.ZERO;

		/**
		 * The encoded (but unpacked) block states. Each index represents a block position and each value represents
		 * an index in the {@link Region#blockStatePalette}.
		 * <p>
		 * Each index is encoded as {@code x + (z * regionSize.x) (y * regionSize.x * regionSize.z)} and can be decoded as follows:
		 * <pre>
		 * int x = index % regionSize.x;
		 * int z = (index / regionSize.x) % regionSize.z;
		 * int y = index / (regionSize.x * regionSize.z);
		 * </pre>
		 *
		 * @see Region#indexToPos(int) to convert an index to a block position
		 * @see Region#posToIndex(int, int, int) to convert a block position to an index
		 */
		public int @NotNull [] blockStates = new int[0];

		/**
		 * The block state palette. Each entry in the array represents a unique block state in this schematic region.
		 * <p>
		 * The values in {@link Region#blockStates} are indices to this array.
		 */
		public SchematicBlock @NotNull [] blockStatePalette = new SchematicBlock[0];

		/**
		 * The block/tile entities in this schematic region.
		 */
		public SchematicBlockEntity @NotNull [] blockEntities = new SchematicBlockEntity[0];

		/**
		 * The entities in this schematic region.
		 */
		public SchematicEntity @NotNull [] entities = new SchematicEntity[0];

		/**
		 * The list of blocks with pending tick calculations.
		 */
		public PendingTicks @NotNull [] pendingBlockTicks = new PendingTicks[0];

		/**
		 * The list of fluids with pending tick calculations.
		 */
		public PendingTicks @NotNull [] pendingFluidTicks = new PendingTicks[0];

		public Region() {
		}

		public int posToIndex(int x, int y, int z) {
			return x + (z * size.x) + (y * size.x * size.z);
		}

		public @NotNull SchematicBlockPos indexToPos(int index) {
			final int x = index % size.x;
			final int z = (index / size.x) % size.z;
			final int y = index / (size.x * size.z);
			return new SchematicBlockPos(x, y, z);
		}

		/**
		 * The region name.
		 *
		 * @return The region name
		 */
		public @Nullable String name() {
			return name;
		}

		/**
		 * The region position in reference to the schematic origin at (0, 0, 0).
		 *
		 * @return The region position
		 */
		public @NotNull SchematicBlockPos position() {
			return position;
		}

		/**
		 * The region size.
		 *
		 * @return The region size
		 */
		public @NotNull SchematicBlockPos size() {
			return size;
		}

		/**
		 * The encoded (but unpacked) block states. Each index represents a block position and each value represents
		 * an index in the {@link Region#blockStatePalette}.
		 * <p>
		 * Each index is encoded as {@code x + (z * regionSize.x) (y * regionSize.x * regionSize.z)} and can be decoded as follows:
		 * <pre>
		 * int x = index % regionSize.x;
		 * int z = (index / regionSize.x) % regionSize.z;
		 * int y = index / (regionSize.x * regionSize.z);
		 * </pre>
		 *
		 * @return The encoded (but unpacked) block states
		 * @see Region#indexToPos(int) to convert an index to a block position
		 * @see Region#posToIndex(int, int, int) to convert a block position to an index
		 */
		public int @NotNull [] blockStates() {
			return blockStates;
		}

		/**
		 * The block state palette. Each entry in the array represents a unique block state in this schematic region.
		 * <p>
		 * The values in {@link Region#blockStates} are indices to this array.
		 *
		 * @return The block state palette
		 */
		public SchematicBlock @NotNull [] blockStatePalette() {
			return blockStatePalette;
		}

		/**
		 * The block/tile entities in this schematic region.
		 *
		 * @return The block entities in this region
		 */
		public SchematicBlockEntity @NotNull [] blockEntities() {
			return blockEntities;
		}

		/**
		 * The entities in this schematic region.
		 *
		 * @return The entities in this region
		 */
		public SchematicEntity @NotNull [] entities() {
			return entities;
		}

		/**
		 * The list of blocks with pending tick calculations.
		 *
		 * @return The blocks pending ticks
		 */
		public PendingTicks @NotNull [] pendingBlockTicks() {
			return pendingBlockTicks;
		}

		/**
		 * The list of fluids with pending tick calculations.
		 *
		 * @return The fluids pending ticks
		 */
		public PendingTicks @NotNull [] pendingFluidTicks() {
			return pendingFluidTicks;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Region region = (Region) o;

			if (!Objects.equals(name, region.name)) return false;
			if (!position.equals(region.position)) return false;
			return size.equals(region.size);
		}

		@Override
		public int hashCode() {
			int result = name != null ? name.hashCode() : 0;
			result = 31 * result + position.hashCode();
			result = 31 * result + size.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return "Region[" +
					"name='" + name + '\'' +
					", position=" + position +
					", size=" + size +
					']';
		}
	}

	/**
	 * Represents a block or fluid pending tick calculations.
	 */
	public static class PendingTicks {
		/**
		 * The pending tick priority.
		 */
		public @Nullable Integer priority;

		/**
		 * The sub-tick.
		 */
		public @Nullable Long subTick;

		/**
		 * The time.
		 */
		public @Nullable Integer time;

		/**
		 * The X coordinate inside the region it is found.
		 */
		public @Nullable Integer x;

		/**
		 * The Y coordinate inside the region it is found.
		 */
		public @Nullable Integer y;

		/**
		 * The Z coordinate inside the region it is found.
		 */
		public @Nullable Integer z;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\Schematic.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.Pair;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBiome;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicItem;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A generic schematic.
 */
public interface Schematic {

	/**
	 * The schematic format.
	 *
	 * @return the schematic format
	 */
	@NotNull SchematicFormat format();

	/**
	 * The width of the schematic, the X axis.
	 *
	 * @return the schematic width
	 */
	int width();

	/**
	 * The height of the schematic, the Y axis.
	 *
	 * @return the schematic height
	 */
	int height();

	/**
	 * The length of the schematic, the Z axis.
	 *
	 * @return the schematic length
	 */
	int length();

	/**
	 * The relative offset from the origin (0, 0, 0) of the schematic.
	 * <br>
	 * Values are stored in the format: [x, y, z]
	 *
	 * @return the schematic offset from the origin
	 */
	@NotNull SchematicBlockPos offset();

	/**
	 * The block at the specified position.
	 * <p>
	 * Depending on the schematic format, each coordinate can also be negative and is relative to the schematic origin.
	 *
	 * @param x The X coordinate, can be a negative value
	 * @param y The Y coordinate, can be a negative value
	 * @param z The Z coordinate, can be a negative value
	 * @return block, or {@code null} if information is not available.
	 */
	@NotNull SchematicBlock block(int x, int y, int z);

	/**
	 * The block at the specified position.
	 * <p>
	 * Depending on the schematic format, each coordinate can also be negative and is relative to the schematic origin.
	 *
	 * @param pos The position
	 * @return block, or {@code null} if information is not available.
	 * @see Schematic#block(int, int, int)
	 */
	default @NotNull SchematicBlock block(@Nullable SchematicBlockPos pos) {
		if (pos == null) {
			return AIR;
		}
		return block(pos.x, pos.y, pos.z);
	}

	/**
	 * Iterate over the list of blocks. Follows a zigzag pattern: first visits X, then Z, then Y.
	 *
	 * @return An iterator over block and position pairs
	 */
	default @NotNull Stream<Pair<SchematicBlockPos, SchematicBlock>> blocks() {
		return IntStream.range(0, width() * length() * height()).mapToObj(index -> {
			final int x = index % width();
			final int z = (index / width()) % length();
			final int y = (index / (width() * length())) % height();
			final SchematicBlockPos pos = new SchematicBlockPos(x, y, z);
			final SchematicBlock block = block(x, y, z);
			return new Pair<>(pos, block);
		});
	}

	/**
	 * The list of tile/block entities, like chests and furnaces.
	 *
	 * @return list of block entities
	 */
	default @NotNull Stream<SchematicBlockEntity> blockEntities() {
		return Stream.empty();
	}

	/**
	 * The list of entities, like players.
	 *
	 * @return list of entities
	 */
	default @NotNull Stream<SchematicEntity> entities() {
		return Stream.empty();
	}

	/**
	 * The biome at the specified position.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param z the Z coordinate
	 * @return biome, or {@code SchematicBiome.AIR} if information is not available.
	 */
	default @NotNull SchematicBiome biome(int x, int y, int z) {
		return SchematicBiome.AIR;
	}

	/**
	 * Iterate over the list of biomes. Follows a zigzag pattern: first visits X, then Z, then Y.
	 *
	 * @return An iterator over biome and position pairs
	 */
	default @NotNull Stream<Pair<SchematicBlockPos, SchematicBiome>> biomes() {
		return IntStream.range(0, width() * length() * height()).mapToObj(index -> {
			final int x = index % width();
			final int z = (index / width()) % length();
			final int y = (index / (width() * length())) % height();
			final SchematicBlockPos pos = new SchematicBlockPos(x, y, z);
			final SchematicBiome biome = biome(x, y, z);
			return new Pair<>(pos, biome);
		});
	}

	/**
	 * The name of the schematic.
	 *
	 * @return schematic name, or {@code null} if information is not available.
	 */
	default @Nullable String name() {
		return null;
	}

	/**
	 * The name of the author of the schematic.
	 *
	 * @return author, or {@code null} if information is not available.
	 */
	default @Nullable String author() {
		return null;
	}

	/**
	 * The date that this schematic was created on. Assumes time is in the UTC timezone.
	 *
	 * @return creation date, or {@code null} if information is not available.
	 */
	default @Nullable LocalDateTime date() {
		return null;
	}

	/**
	 * The icon for representing this schematic. A Minecraft item.
	 *
	 * @return icon, or {@code null} if information is not available.
	 */
	default @Nullable SchematicItem icon() {
		return null;
	}

	/**
	 * The schematic format.
	 *
	 * @return the schematic format
	 * @deprecated Use {@link Schematic#format()} instead
	 */
	@Deprecated
	default SchematicFormat getFormat() {
		return format();
	}

	/**
	 * The width of the schematic.
	 *
	 * @return the schematic width
	 * @deprecated Use {@link Schematic#width()} instead
	 */
	@Deprecated
	default int getWidth() {
		return width();
	}

	/**
	 * The height of the schematic.
	 *
	 * @return the schematic height
	 * @deprecated Use {@link Schematic#height()} instead
	 */
	@Deprecated
	default int getHeight() {
		return height();
	}

	/**
	 * The length of the schematic.
	 *
	 * @return the schematic length
	 * @deprecated Use {@link Schematic#length()} instead
	 */
	@Deprecated
	default int getLength() {
		return length();
	}

	/**
	 * The relative offset from the origin (0, 0, 0) of the schematic.
	 * <br>
	 * Values are stored in the format: [x, y, z]
	 *
	 * @return the schematic offset from the origin
	 * @deprecated Use {@link Schematic#offset()} instead
	 */
	@Deprecated
	default int[] getOffset() {
		final SchematicBlockPos offset = offset();
		return new int[]{offset.x, offset.y, offset.z};
	}

	/**
	 * The block at the specified block.
	 * <p>
	 * Depending on the schematic format, each coordinate can also be negative and is relative to the schematic origin.
	 *
	 * @param x The X coordinate, can be a negative value
	 * @param y The X coordinate, can be a negative value
	 * @param z The X coordinate, can be a negative value
	 * @return block, or {@code null} if information is not available.
	 * @deprecated Use {@link Schematic#block(int, int, int)} instead
	 */
	@Deprecated
	default SchematicBlock getBlock(int x, int y, int z) {
		return block(x, y, z);
	}

	/**
	 * Iterator for iterating over the list of blocks.
	 *
	 * @return an iterator
	 * @deprecated Use {@link Schematic#blocks()} instead
	 */
	@Deprecated
	default Iterator<SchematicBlock> getBlocks() {
		return blocks().map(pair -> pair.right).iterator();
	}

	/**
	 * The list of tile/block entities, like chests and furnaces.
	 *
	 * @return list of block entities
	 * @deprecated Use {@link Schematic#blockEntities()} instead
	 */
	@Deprecated
	default Collection<SchematicBlockEntity> getBlockEntities() {
		return blockEntities().collect(Collectors.toList());
	}

	/**
	 * The list of entities, like players.
	 *
	 * @return list of entities
	 * @deprecated Use {@link Schematic#entities()} instead
	 */
	@Deprecated
	default Collection<SchematicEntity> getEntities() {
		return entities().collect(Collectors.toList());
	}

	/**
	 * The biome at the specified block.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param z the Z coordinate
	 * @return biome, or {@code null} if information is not available.
	 * @deprecated Use {@link Schematic#biome(int, int, int)}  instead
	 */
	@Deprecated
	default SchematicBiome getBiome(int x, int y, int z) {
		return biome(x, y, z);
	}

	/**
	 * Iterator for iterating over the list of biomes.
	 *
	 * @return an iterator
	 * @deprecated Use {@link Schematic#biomes()} instead
	 */
	@Deprecated
	default Iterator<SchematicBiome> getBiomes() {
		return biomes().map(p -> p.right).iterator();
	}

	/**
	 * The name of the schematic.
	 *
	 * @return schematic name, or {@code null} if information is not available.
	 * @deprecated Use {@link Schematic#name()}  instead
	 */
	@Deprecated
	default String getName() {
		return name();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\SchematicaSchematic.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicItem;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Schematica schematic. Read more about it at <a href="https://minecraft.fandom.com/wiki/Schematic_file_format">https://minecraft.fandom.com/wiki/Schematic_file_format</a>
 * <br>
 * <h2>Implementations</h2>
 * <ul>
 *     <li><a href="https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/extent/clipboard/io/MCEditSchematicReader.java">WorldEdit</a></li>
 *     <li><a href="https://github.com/mcedit/pymclevel/blob/master/schematic.py">MCEdit</a></li>
 *     <li><a href="https://github.com/mcedit/mcedit2/blob/master/src/mceditlib/schematic.py">MCEdit2</a></li>
 *     <li><a href="https://github.com/Khroki/MCEdit-Unified/blob/master/pymclevel/schematic.py">MCEdit-Unified</a></li>
 *     <li><a href="https://github.com/Lunatrius/Schematica/blob/master/src/main/java/com/github/lunatrius/schematica/world/schematic/SchematicAlpha.java">Schematica</a></li>
 *     <li><a href="https://github.com/CzechPMDevs/BuilderTools">BuilderTools - PocketMine</a></li>
 * </ul>
 */
public class SchematicaSchematic implements Schematic {

	public static final String MATERIAL_CLASSIC = "Classic";
	public static final String MATERIAL_ALPHA = "Alpha";
	public static final String MATERIAL_STRUCTURE = "Structure";

	/**
	 * The schematic width, the X axis.
	 */
	public int width;

	/**
	 * The schematic height, the Y axis.
	 */
	public int height;

	/**
	 * The schematic length, the Z axis.
	 */
	public int length;

	/**
	 * The unpacked list of block IDs.
	 */
	public int @NotNull [] blockIds = new int[0];

	/**
	 * The unpacked list of block metadata (used as discriminator before Minecraft's 1.7 block ID overhaul).
	 */
	public int @NotNull [] blockMetadata = new int[0];

	/**
	 * The unpacked list of blocks.
	 */
	public String @NotNull [] blockPalette = new String[0];

	/**
	 * The list of block/tile entities.
	 */
	public @NotNull SchematicBlockEntity @NotNull [] blockEntities = new SchematicBlockEntity[0];

	/**
	 * The list of entities.
	 */
	public @NotNull SchematicEntity @NotNull [] entities = new SchematicEntity[0];

	/**
	 * The schematic icon, if available.
	 */
	public @Nullable SchematicItem icon;

	/**
	 * The schematic materials, if available.
	 * <p>
	 * One of:
	 * <ul>
	 *     <li>{@link SchematicaSchematic#MATERIAL_CLASSIC MATERIAL_CLASSIC}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_ALPHA MATERIAL_ALPHA}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_STRUCTURE MATERIAL_STRUCTURE}</li>
	 * </ul>
	 */
	public @Nullable String materials;

	public SchematicaSchematic() {
	}

	@Override
	public @NotNull SchematicFormat format() {
		return SchematicFormat.SCHEMATICA;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public @NotNull SchematicBlockPos offset() {
		return SchematicBlockPos.ZERO;
	}

	@Override
	public @NotNull SchematicBlock block(int x, int y, int z) {
		final int blockIndex = posToIndex(x, y, z);
		if (blockIndex < 0 || blockIndex >= blockIds.length) {
			return AIR; // outside bounds
		}

		final int blockId = blockIds[blockIndex];
		String blockName = blockPalette[blockId];
		if (blockName == null) {
			blockName = "minecraft:legacy_id_" + blockId;
		}

		final int metadata = blockMetadata[blockIndex];
		final Map<String, String> states = new TreeMap<>();
		if (metadata != 0) {
			states.put("metadata", String.valueOf(metadata));
		}

		return new SchematicBlock(blockName, states);
	}

	/**
	 * The raw block ID data.
	 *
	 * @return The raw block data
	 */
	public int @NotNull [] blockIdData() {
		return blockIds;
	}

	/**
	 * The raw block metadata.
	 *
	 * @return The raw block data
	 */
	public int @NotNull [] blockMetadata() {
		return blockMetadata;
	}

	/**
	 * The raw block palette.
	 *
	 * @return The raw block palette
	 */
	public String @NotNull [] blockPalette() {
		return blockPalette;
	}

	@Override
	public @NotNull Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(blockEntities);
	}

	/**
	 * The raw block entity data.
	 *
	 * @return The raw block entity data
	 */
	public @NotNull SchematicBlockEntity[] blockEntityData() {
		return blockEntities;
	}

	@Override
	public @NotNull Stream<SchematicEntity> entities() {
		return Arrays.stream(entities);
	}

	/**
	 * The raw entity data.
	 *
	 * @return The raw entity data
	 */
	public @NotNull SchematicEntity[] entityData() {
		return entities;
	}

	@Override
	public @Nullable SchematicItem icon() {
		return icon;
	}

	/**
	 * The schematic materials, if available.
	 * <p>
	 * One of:
	 * <ul>
	 *     <li>{@link SchematicaSchematic#MATERIAL_CLASSIC MATERIAL_CLASSIC}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_ALPHA MATERIAL_ALPHA}</li>
	 *     <li>{@link SchematicaSchematic#MATERIAL_STRUCTURE MATERIAL_STRUCTURE}</li>
	 * </ul>
	 *
	 * @return The schematic materials, if available
	 */
	public @Nullable String materials() {
		return materials;
	}

	public int posToIndex(int x, int y, int z) {
		return x + (z * width) + (y * width * length);
	}

	public @NotNull SchematicBlockPos indexToPos(int index) {
		final int x = index % width;
		final int z = (index / width) % length;
		final int y = index / (width * length);
		return new SchematicBlockPos(x, y, z);
	}

	@Override
	public String toString() {
		return "SchematicSchematica[" +
				"name=" + name() +
				", width=" + width +
				", height=" + height +
				", length=" + length +
				']';
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\SpongeSchematic.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.SchematicFormat;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.Pair;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBiome;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockEntity;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlockPos;
import cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicEntity;

import static cn.myfrank.stationbuilder.schematic4j.schematic.types.SchematicBlock.AIR;

/**
 * A Sponge schematic. Read more about it at <a href="https://github.com/SpongePowered/Schematic-Specification">https://github.com/SpongePowered/Schematic-Specification</a>.
 */
public class SpongeSchematic implements Schematic {

	/**
	 * The Sponge Schematic format version being used.
	 **/
	public int version = 1;

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 */
	public @Nullable Integer dataVersion;

	/**
	 * The optional metadata about the schematic.
	 */
	public @NotNull Metadata metadata = new Metadata();

	/**
	 * The width (the size of the area in the X-axis) of the schematic.
	 */
	public int width;

	/**
	 * The height (the size of the area in the Y-axis) of the schematic.
	 */
	public int height;

	/**
	 * The length (the size of the area in the Z-axis) of the schematic.
	 */
	public int length;

	/**
	 * The relative offset of the schematic from the paster. When pasting, if there is a reasonable location to use as
	 * a base position, implementations SHOULD offset the location of the paste by this vector. The default value if
	 * not provided is [0, 0, 0]. Example: If a player is pasting from 1, 2, 3, and the offset is 4, 5, 6, then the
	 * first block should be placed at 5, 7, 9
	 */
	public @NotNull SchematicBlockPos offset = SchematicBlockPos.ZERO;

	/**
	 * The unpacked block data indices.
	 */
	public int @NotNull [] blocks = new int[0];

	/**
	 * The unpacked block data indices.
	 */
	public SchematicBlock @NotNull [] blockPalette = new SchematicBlock[0];

	/**
	 * The block/tile entity data.
	 */
	public SchematicBlockEntity @NotNull [] blockEntities = new SchematicBlockEntity[0];

	/**
	 * The entity data.
	 */
	public SchematicEntity @NotNull [] entities = new SchematicEntity[0];

	/**
	 * The unpacked biome data.
	 */
	public int @NotNull [] biomes = new int[0];

	/**
	 * The biome palette data.
	 */
	public @NotNull SchematicBiome[] biomePalette = new SchematicBiome[0];

	public SpongeSchematic() {
	}

	@Override
	public @NotNull SchematicFormat format() {
		switch (version) {
			case 1:
				return SchematicFormat.SPONGE_V1;
			case 2:
				return SchematicFormat.SPONGE_V2;
			default:
			case 3:
				return SchematicFormat.SPONGE_V3;
		}
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public int length() {
		return length;
	}

	@Override
	public @NotNull SchematicBlockPos offset() {
		return offset;
	}

	@Override
	public @NotNull SchematicBlock block(int x, int y, int z) {
		final int blockIndex = posToIndex(x, y, z);
		if (blockIndex < 0 || blockIndex >= blocks.length) {
			return AIR; // outside bounds
		}

		final int paletteIndex = blocks[blockIndex];
		return blockPalette[paletteIndex];
	}

	/**
	 * The raw block data.
	 *
	 * @return The raw block data
	 */
	public int @NotNull [] blockData() {
		return blocks;
	}

	/**
	 * The raw block palette.
	 *
	 * @return The raw block palette
	 */
	public SchematicBlock @NotNull [] blockPalette() {
		return blockPalette;
	}

	@Override
	public @NotNull Stream<SchematicBlockEntity> blockEntities() {
		return Arrays.stream(blockEntities);
	}

	/**
	 * The raw block entity data.
	 *
	 * @return The raw block data
	 */
	public @NotNull SchematicBlockEntity[] blockEntityData() {
		return blockEntities;
	}

	@Override
	public @NotNull Stream<SchematicEntity> entities() {
		return Arrays.stream(entities);
	}

	/**
	 * The raw entity data.
	 *
	 * @return The raw block data
	 */
	public SchematicEntity @NotNull [] entityData() {
		return entities;
	}

	@Override
	public @NotNull SchematicBiome biome(int x, int y, int z) {
		// 3D biome data is only available starting in v3. Flatten the y coordinate for older versions
		if (version <= 2) {
			y = 0;
		}

		final int biomeIndex = posToIndex(x, y, z);
		if (biomeIndex < 0 || biomeIndex >= biomes.length) {
			return SchematicBiome.AIR; // outside bounds
		}

		final int paletteIndex = biomes[biomeIndex];
		return biomePalette[paletteIndex];
	}

	/**
	 * Iterate over the list of biomes. Follows a zigzag pattern: first visits X, then Z, then Y.
	 *
	 * @return an iterator
	 */
	@Override
	public @NotNull Stream<Pair<SchematicBlockPos, SchematicBiome>> biomes() {
		return IntStream.range(0, biomes.length).mapToObj(index -> {
			SchematicBlockPos pos = indexToPos(index);
			SchematicBiome biome = biomePalette[biomes[index]];
			return new Pair<>(pos, biome);
		});
	}

	/**
	 * The raw biome data.
	 *
	 * @return The raw biome data
	 */
	public int @NotNull [] biomeData() {
		return biomes;
	}

	/**
	 * The raw biome palette.
	 *
	 * @return The raw biome palette
	 */
	public SchematicBiome @NotNull [] biomePalette() {
		return biomePalette;
	}

	@Override
	public @Nullable String name() {
		return metadata.name;
	}

	@Override
	public @Nullable String author() {
		return metadata.author;
	}

	@Override
	public @Nullable LocalDateTime date() {
		return metadata.date;
	}

	/**
	 * Specifies the data version of Minecraft that was used to create the schematic.
	 * <p>
	 * This is to allow for block and entity data to be validated and auto-converted from older versions.
	 * This is dependent on the Minecraft version, e.g. Minecraft 1.12.2's data version is
	 * <a href="https://minecraft.gamepedia.com/1.12.2">1343</a>.
	 *
	 * @return The Minecraft data version
	 */
	public @Nullable Integer dataVersion() {
		return dataVersion;
	}

	/**
	 * @deprecated Use {@link SpongeSchematic#dataVersion()} instead
	 */
	@Deprecated
	public @Nullable Integer getDataVersion() {
		return dataVersion();
	}

	/**
	 * The optional metadata about the schematic.
	 *
	 * @return The schematic metadata
	 */
	public @NotNull Metadata metadata() {
		return metadata;
	}

	/**
	 * @deprecated Use {@link SpongeSchematic#metadata()} instead
	 */
	@Deprecated
	public @NotNull Metadata getMetadata() {
		return metadata();
	}

	public int posToIndex(int x, int y, int z) {
		return x + (z * width) + (y * width * length);
	}

	public @NotNull SchematicBlockPos indexToPos(int index) {
		final int x = index % width;
		final int z = (index / width) % length;
		final int y = index / (width * length);
		return new SchematicBlockPos(x, y, z);
	}

	@Override
	public String toString() {
		return "SchematicSponge[" +
				"name=" + name() +
				", version=" + version +
				", width=" + width +
				", height=" + height +
				", length=" + length +
				']';
	}

	/**
	 * The schematic metadata.
	 */
	public static class Metadata {
		/**
		 * The name of the schematic.
		 */
		public @Nullable String name;

		/**
		 * The name of the author of the schematic.
		 */
		public @Nullable String author;

		/**
		 * The date that this schematic was created on.
		 */
		public @Nullable LocalDateTime date;

		/**
		 * An array of mod IDs.
		 */
		public String @NotNull [] requiredMods = new String[0];

		/**
		 * Extra metadata not represented in the specification.
		 */
		public @NotNull Map<String, Object> extra = new TreeMap<>();

		public Metadata() {
		}

		@Override
		public String toString() {
			return "Metadata[" +
					"name='" + name + '\'' +
					", author='" + author + '\'' +
					", date=" + date +
					", requiredMods=" + Arrays.toString(requiredMods) +
					", extra=" + extra +
					']';
		}
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\utils\DateUtils.java

```java
package cn.myfrank.stationbuilder.schematic4j.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Collection of utility functions to work with dates.
 */
public class DateUtils {

	/**
	 * Convert an epoch time into a {@linkplain LocalDateTime}.
	 *
	 * @param epoch The epoch time, in milliseconds
	 * @return The {@linkplain LocalDateTime}
	 */
	public static LocalDateTime epochToDate(long epoch) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.of("UTC"));
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\utils\TagUtils.java

```java
package cn.myfrank.stationbuilder.schematic4j.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import cn.myfrank.stationbuilder.schematic4j.exception.MissingFieldException;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.NumberTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * Collection of utility functions to work with NBT tags.
 */
public class TagUtils {

	private TagUtils() {}

	public static Object unwrap(Tag<?> value) {
		if (value instanceof StringTag) {
			return ((StringTag) value).getValue();
		} else if (value instanceof LongTag) {
			return ((LongTag) value).asLong();
		} else if (value instanceof IntTag) {
			return ((IntTag) value).asInt();
		} else if (value instanceof ShortTag) {
			return ((ShortTag) value).asShort();
		} else if (value instanceof ByteTag) {
			return ((ByteTag) value).asByte();
		} else if (value instanceof FloatTag) {
			return ((FloatTag) value).asFloat();
		} else if (value instanceof DoubleTag) {
			return ((DoubleTag) value).asDouble();
		} else if (value instanceof IntArrayTag) {
			return ((IntArrayTag) value).getValue();
		} else if (value instanceof ByteArrayTag) {
			return ((ByteArrayTag) value).getValue();
		} else if (value instanceof LongArrayTag) {
			return ((LongArrayTag) value).getValue();
		} else if (value instanceof CompoundTag) {
			final CompoundTag compoundTag = (CompoundTag) value;
			final Map<String, Object> map = new TreeMap<>();
			for (final Map.Entry<String, Tag<?>> entry : compoundTag) {
				map.put(entry.getKey(), unwrap(entry.getValue()));
			}
			return map;
		} else if (value instanceof ListTag<?>) {
			final ListTag<?> listTag = (ListTag<?>) value;
			final List<Object> list = new ArrayList<>(listTag.size());
			for (Tag<?> tag : listTag) {
				list.add(unwrap(tag));
			}
			return list;
		} else {
			return value;
		}
	}

	public static boolean containsAllTags(CompoundTag tag, String... requiredTags) {
		return Arrays.stream(requiredTags).allMatch(tag::containsKey);
	}

	public static boolean containsTag(CompoundTag tag, String... optionalTags) {
		return Arrays.stream(optionalTags).anyMatch(tag::containsKey);
	}


	public static Optional<Integer> getInt(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getIntTag(key)).map(NumberTag::asInt);
	}

	public static Optional<Short> getShort(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getShortTag(key)).map(NumberTag::asShort);
	}

	public static Optional<Byte> getByte(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getByteTag(key)).map(NumberTag::asByte);
	}

	public static Optional<Long> getLong(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getLongTag(key)).map(NumberTag::asLong);
	}

	public static Optional<Float> getFloat(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getFloatTag(key)).map(NumberTag::asFloat);
	}

	public static Optional<Double> getDouble(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getDoubleTag(key)).map(NumberTag::asDouble);
	}

	public static Optional<int[]> getIntArray(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getIntArrayTag(key)).map(IntArrayTag::getValue);
	}

	public static Optional<byte[]> getByteArray(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getByteArrayTag(key)).map(ByteArrayTag::getValue);
	}

	public static Optional<long[]> getLongArray(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getLongArrayTag(key)).map(LongArrayTag::getValue);
	}

	public static Optional<ListTag<FloatTag>> getFloatList(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getListTag(key)).map(ListTag::asFloatTagList);
	}

	public static Optional<ListTag<DoubleTag>> getDoubleList(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getListTag(key)).map(ListTag::asDoubleTagList);
	}

	public static Optional<ListTag<CompoundTag>> getCompoundList(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getListTag(key)).map(ListTag::asCompoundTagList);
	}

	public static Optional<String> getString(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getStringTag(key)).map(StringTag::getValue);
	}

	public static Optional<CompoundTag> getCompound(CompoundTag tag, String key) {
		return Optional.ofNullable(tag.getCompoundTag(key));
	}


	public static int getIntOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getInt(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, IntTag.class));
	}

	public static short getShortOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getShort(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ShortTag.class));
	}

	public static byte getByteOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getByte(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ByteTag.class));
	}

	public static long getLongOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getLong(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, LongTag.class));
	}

	public static float getFloatOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getFloat(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, FloatTag.class));
	}

	public static double getDoubleOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getDouble(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, DoubleTag.class));
	}

	public static int[] getIntArrayOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getIntArray(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, IntArrayTag.class));
	}

	public static byte[] getByteArrayOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getByteArray(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ByteArrayTag.class));
	}

	public static long[] getLongArrayOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getLongArray(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, LongArrayTag.class));
	}

	public static ListTag<FloatTag> getFloatListOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getFloatList(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ListTag.class));
	}

	public static ListTag<DoubleTag> getDoubleListOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getDoubleList(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ListTag.class));
	}

	public static ListTag<CompoundTag> getCompoundListOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getCompoundList(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, ListTag.class));
	}

	public static String getStringOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getString(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, StringTag.class));
	}

	public static CompoundTag getCompoundOrThrow(CompoundTag tag, String key) throws MissingFieldException {
		return getCompound(tag, key).orElseThrow(() -> new MissingFieldException(tag, key, CompoundTag.class));
	}

}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\Pair.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Objects;

public class Pair<L, R> {
	public L left;
	public R right;

	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public L left() {
		return left;
	}

	public R right() {
		return right;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Pair<?, ?> pair = (Pair<?, ?>) o;

		if (!Objects.equals(left, pair.left)) return false;
		return Objects.equals(right, pair.right);
	}

	@Override
	public int hashCode() {
		int result = left != null ? left.hashCode() : 0;
		result = 31 * result + (right != null ? right.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Pair[" + left + ", " + right + ']';
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicBiome.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

/**
 * Represents a biome as a resource ID, like "minecraft:plains".
 */
public class SchematicBiome extends SchematicBlock {

	public static final SchematicBiome AIR = new SchematicBiome("minecraft:air");

	public SchematicBiome(String blockstate) {
		super(blockstate);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicBlock.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a block as a resource identifier plus block states, like "minecraft:dirt" or "minecraft:chest[facing=south]".
 */
public class SchematicBlock extends SchematicNamed {

	public static final SchematicBlock AIR = new SchematicBlock("minecraft:air");

	/**
	 * The block name excluding block states.
	 * <br>
	 * For example, the block state "minecraft:chest[facing=south]" would become just "minecraft:chest".
	 */
	public @NotNull String block;

	/**
	 * The list of block states or properties.
	 * <br>
	 * For example, chests have the "facing", "type" and "waterlogged" properties.
	 */
	public @NotNull Map<String, String> states;

	public SchematicBlock(@NotNull String block, @NotNull Map<String, String> states) {
		super(blockNameAndStatesToString(block, states));
		this.block = block;
		this.states = states;
	}

	public SchematicBlock(String blockAndStates) {
		this(extractBlockName(blockAndStates), extractBlockStates(blockAndStates));
	}

	/**
	 * The block name excluding block states.
	 * <br>
	 * For example, the block state "minecraft:chest[facing=south]" would become just "minecraft:chest".
	 *
	 * @return The block name
	 */
	public @NotNull String block() {
		return block;
	}

	/**
	 * The list of block states or properties.
	 * <br>
	 * For example, chests have the "facing", "type" and "waterlogged" properties.
	 *
	 * @return The block states
	 */
	public @NotNull Map<String, String> states() {
		return states;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		SchematicBlock that = (SchematicBlock) o;

		if (!block.equals(that.block)) return false;
		return states.equals(that.states);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + block.hashCode();
		result = 31 * result + states.hashCode();
		return result;
	}

	public static String extractBlockName(String blockAndStates) {
		int openingBracketPos = blockAndStates.indexOf('[');
		char lastChar = blockAndStates.charAt(blockAndStates.length() - 1);
		if (openingBracketPos != -1 && lastChar == ']') {
			return blockAndStates.substring(0, openingBracketPos);
		} else {
			return blockAndStates;
		}
	}

	public static Map<String, String> extractBlockStates(String blockAndStates) {
		int openingBracketPos = blockAndStates.indexOf('[');
		char lastChar = blockAndStates.charAt(blockAndStates.length() - 1);
		if (openingBracketPos == -1 || lastChar != ']') {
			return Collections.emptyMap();
		}

		final Map<String, String> states = new TreeMap<>();

		final String[] statesRaw = blockAndStates.substring(openingBracketPos + 1, blockAndStates.length() - 1).split(",");
		for (String state : statesRaw) {
			int separatorIndex = state.indexOf('=');
			if (separatorIndex != -1) {
				String name = state.substring(0, separatorIndex);
				String value = state.substring(separatorIndex + 1);
				states.put(name, value);
			} else {
				states.put(state, "");
			}
		}

		return states;
	}

	public static String blockNameAndStatesToString(@NotNull String block, Map<String, String> states) {
		if (states.isEmpty()) {
			return block;
		}

		final StringBuilder statesStr = new StringBuilder();
		for (Map.Entry<String, String> entry : states.entrySet()) {
			if (statesStr.length() > 0) {
				statesStr.append(',');
			}
			statesStr.append(entry.getKey()).append('=').append(entry.getValue());
		}

		return block + '[' + statesStr + ']';
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicBlockEntity.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

import static java.util.stream.Collectors.toMap;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.unwrap;

/**
 * Represents a block/tile entity, like a chest or a furnace.
 */
public class SchematicBlockEntity extends SchematicNamed {

	/**
	 * The position where they can be found in the schematic.
	 */
	public SchematicBlockPos pos;

	/**
	 * Extra NBT data, like the items stored in a chest, if available.
	 */
	public Map<String, Object> data;

	public SchematicBlockEntity(String name, SchematicBlockPos pos, Map<String, Object> data) {
		super(name);
		this.pos = pos;
		this.data = data;
	}

	public static @Nullable SchematicBlockEntity fromNbt(Tag<?> nbtTag) {
		if (!(nbtTag instanceof CompoundTag)) {
			return null;
		}
		final CompoundTag nbt = ((CompoundTag) nbtTag);

		final String id = nbt.getString("id");
		final SchematicBlockPos pos = SchematicBlockPos.from(nbt);
		final Map<String, Object> extra = nbt.entrySet().stream()
				.filter(tag -> !tag.getKey().equals("id") &&
						!tag.getKey().equals("x") &&
						!tag.getKey().equals("y") &&
						!tag.getKey().equals("z"))
				.collect(toMap(Map.Entry::getKey, e -> unwrap(e.getValue()), (a, b) -> b, TreeMap::new));

		return new SchematicBlockEntity(id, pos, extra);
	}

	/**
	 * The position where they can be found in the schematic.
	 *
	 * @return The block entity position
	 */
	public SchematicBlockPos pos() {
		return pos;
	}

	/**
	 * Extra NBT data, like the items stored in a chest, if available.
	 *
	 * @return The NBT data
	 */
	public Map<String, Object> extra() {
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		SchematicBlockEntity that = (SchematicBlockEntity) o;

		if (!pos.equals(that.pos)) return false;
		return data.equals(that.data);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + pos.hashCode();
		result = 31 * result + data.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[name=" + name + ", pos=" + pos + ", data=" + data + ']';
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicBlockPos.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Comparator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * Represents a block position.
 */
public class SchematicBlockPos implements Comparable<SchematicBlockPos> {

	public static final SchematicBlockPos ZERO = new SchematicBlockPos(0, 0, 0);

	/**
	 * The X coordinate.
	 */
	public final int x;

	/**
	 * The Y coordinate.
	 */
	public final int y;

	/**
	 * The Z coordinate.
	 */
	public final int z;

	public SchematicBlockPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public SchematicBlockPos(int[] pos) {
		this(pos[0], pos[1], pos[2]);
	}

	public SchematicBlockPos(SchematicBlockPos other) {
		this(other.x, other.y, other.z);
	}

	public static SchematicBlockPos from(int x, int y, int z) {
		return new SchematicBlockPos(x, y, z);
	}

	public static SchematicBlockPos from(int[] pos) {
		return from(pos[0], pos[1], pos[2]);
	}

	public static @Nullable SchematicBlockPos from(Tag<?> nbtTag) {
		if (!(nbtTag instanceof CompoundTag)) {
			return null;
		}
		final CompoundTag nbt = ((CompoundTag) nbtTag);
		final int x = nbt.getInt("x");
		final int y = nbt.getInt("y");
		final int z = nbt.getInt("z");
		return new SchematicBlockPos(x, y, z);
	}

	public static SchematicBlockPos from(SchematicBlockPos other) {
		return new SchematicBlockPos(other);
	}

	/**
	 * The X coordinate.
	 *
	 * @return The X coordinate
	 */
	public int x() {
		return x;
	}

	/**
	 * The Y coordinate.
	 *
	 * @return The Y coordinate
	 */
	public int y() {
		return y;
	}

	/**
	 * The Z coordinate.
	 *
	 * @return The Z coordinate
	 */
	public int z() {
		return z;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SchematicBlockPos that = (SchematicBlockPos) o;

		if (x != that.x) return false;
		if (y != that.y) return false;
		return z == that.z;
	}

	@Override
	public int hashCode() {
		int result = x;
		result = 31 * result + y;
		result = 31 * result + z;
		return result;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ')';
	}

	@Override
	public int compareTo(@NotNull SchematicBlockPos o) {
		return Comparator.nullsLast(
				Comparator.<SchematicBlockPos>comparingInt(obj -> obj.x)
						.thenComparingInt(obj -> obj.y)
						.thenComparingInt(obj -> obj.z)
		).compare(this, o);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicEntity.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

import static java.util.stream.Collectors.toMap;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.getString;
import static cn.myfrank.stationbuilder.schematic4j.utils.TagUtils.unwrap;

/**
 * Represents an entity, like a creeper.
 */
public class SchematicEntity extends SchematicNamed {

	/**
	 * The position of the entity on the schematic.
	 */
	public SchematicEntityPos pos;

	/**
	 * The extra NBT data the entity is holding, like a wolf's owner.
	 */
	public Map<String, Object> data;

	public SchematicEntity(String name, SchematicEntityPos pos, Map<String, Object> data) {
		super(name);
		this.pos = pos;
		this.data = data;
	}

	public static @Nullable SchematicEntity fromNbt(Tag<?> nbtTag) {
		if (!(nbtTag instanceof CompoundTag)) {
			return null;
		}
		final CompoundTag nbt = ((CompoundTag) nbtTag);

		final String id = getString(nbt, "id").orElseGet(() -> nbt.getString("Id"));
		final SchematicEntityPos pos = SchematicEntityPos.from(nbt.get("Pos"));
		final Map<String, Object> extra = nbt.entrySet().stream()
				.filter(tag -> !tag.getKey().equals("id") && !tag.getKey().equals("Id") && !tag.getKey().equals("Pos"))
				.collect(toMap(Map.Entry::getKey, e -> unwrap(e.getValue()), (a, b) -> b, TreeMap::new));

		return new SchematicEntity(id, pos, extra);
	}

	/**
	 * The position of the entity on the schematic.
	 *
	 * @return The position of the entity
	 */
	public SchematicEntityPos pos() {
		return pos;
	}

	/**
	 * The extra NBT data the entity is holding, like a wolf's owner.
	 *
	 * @return The extra NBT data
	 */
	public Map<String, Object> data() {
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		SchematicEntity that = (SchematicEntity) o;

		if (!Objects.equals(pos, that.pos)) return false;
		return Objects.equals(data, that.data);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (pos != null ? pos.hashCode() : 0);
		result = 31 * result + (data != null ? data.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "SchematicEntity[name=" + name + ", pos=" + pos + ", data=" + data + ']';
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicEntityPos.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Comparator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * Represents an entity position with decimal place precision.
 */
public class SchematicEntityPos implements Comparable<SchematicEntityPos> {

	/**
	 * The X coordinate.
	 */
	public double x;

	/**
	 * The Y coordinate.
	 */
	public double y;

	/**
	 * The Z coordinate.
	 */
	public double z;

	public SchematicEntityPos(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public SchematicEntityPos(double[] pos) {
		this(pos[0], pos[1], pos[2]);
	}

	public SchematicEntityPos(SchematicEntityPos other) {
		this(other.x, other.y, other.z);
	}

	public static SchematicEntityPos from(double x, double y, double z) {
		return new SchematicEntityPos(x, y, z);
	}

	public static SchematicEntityPos from(double[] pos) {
		return from(pos[0], pos[1], pos[2]);
	}

	@SuppressWarnings("unchecked")
	public static @Nullable SchematicEntityPos from(Tag<?> nbtTag) {
		if (!(nbtTag instanceof ListTag<?>)) {
			return null;
		}
		final ListTag<DoubleTag> nbt = ((ListTag<DoubleTag>) nbtTag);
		final double x = nbt.get(0).asDouble();
		final double y = nbt.get(1).asDouble();
		final double z = nbt.get(2).asDouble();
		return new SchematicEntityPos(x, y, z);
	}

	public static SchematicEntityPos from(SchematicEntityPos other) {
		return new SchematicEntityPos(other);
	}

	/**
	 * The X coordinate.
	 *
	 * @return The X coordinate
	 */
	public double x() {
		return x;
	}

	/**
	 * The Y coordinate.
	 *
	 * @return The Y coordinate
	 */
	public double y() {
		return y;
	}

	/**
	 * The Z coordinate.
	 *
	 * @return The Z coordinate
	 */
	public double z() {
		return z;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SchematicEntityPos that = (SchematicEntityPos) o;

		if (Double.compare(x, that.x) != 0) return false;
		if (Double.compare(y, that.y) != 0) return false;
		return Double.compare(z, that.z) == 0;
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		temp = Double.doubleToLongBits(x);
		result = (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(z);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ')';
	}

	@Override
	public int compareTo(@NotNull SchematicEntityPos o) {
		return Comparator.nullsLast(
				Comparator.<SchematicEntityPos>comparingDouble(obj -> obj.x)
						.thenComparingDouble(obj -> obj.y)
						.thenComparingDouble(obj -> obj.z)
		).compare(this, o);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicItem.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

/**
 * Represents an item, like a stick.
 */
public class SchematicItem extends SchematicNamed {

	/**
	 * The item count, like a stack of 64 sticks.
	 */
	public int count;

	/**
	 * The damage amount.
	 */
	public int damage;

	public SchematicItem(String name, int count, int damage) {
		super(name);
		this.count = count;
		this.damage = damage;
	}

	/**
	 * The item count, like a stack of 64 sticks.
	 *
	 * @return The item count
	 */
	public int count() {
		return count;
	}

	/**
	 * The damage amount.
	 *
	 * @return The damage amount
	 */
	public int damage() {
		return damage;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		SchematicItem that = (SchematicItem) o;

		return count == that.count;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + count;
		return result;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[name=" + name + ", count=" + count + ", damage=" + damage + "]";
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\schematic\types\SchematicNamed.java

```java
package cn.myfrank.stationbuilder.schematic4j.schematic.types;

import java.util.Comparator;

import org.jetbrains.annotations.NotNull;

public abstract class SchematicNamed implements Comparable<SchematicNamed> {

	/**
	 * The resource name, usually as a resource identifier like "minecraft:dirt".
	 */
	public @NotNull String name;

	public SchematicNamed(@NotNull String name) {
		this.name = name;
	}

	/**
	 * The resource name.
	 *
	 * @return The resource name
	 */
	public @NotNull String name() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SchematicNamed that = (SchematicNamed) o;

		return name.equals(that.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + '[' + name + ']';
	}

	@Override
	public int compareTo(@NotNull SchematicNamed o) {
		return Comparator.nullsLast(Comparator.<SchematicNamed, String>comparing(obj -> obj.name)).compare(this, o);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\LittleEndianNBTInputStream.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.myfrank.stationbuilder.schematic4j.nbt.ExceptionBiFunction;
import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class LittleEndianNBTInputStream implements DataInput, NBTInput, MaxDepthIO, Closeable {

	private final DataInputStream input;

	private static final Map<Byte, ExceptionBiFunction<LittleEndianNBTInputStream, Integer, ? extends Tag<?>, IOException>> readers = new HashMap<>();
	private static final Map<Byte, Class<?>> idClassMapping = new HashMap<>();

	static {
		put(EndTag.ID, (i, d) -> EndTag.INSTANCE, EndTag.class);
		put(ByteTag.ID, (i, d) -> readByte(i), ByteTag.class);
		put(ShortTag.ID, (i, d) -> readShort(i), ShortTag.class);
		put(IntTag.ID, (i, d) -> readInt(i), IntTag.class);
		put(LongTag.ID, (i, d) -> readLong(i), LongTag.class);
		put(FloatTag.ID, (i, d) -> readFloat(i), FloatTag.class);
		put(DoubleTag.ID, (i, d) -> readDouble(i), DoubleTag.class);
		put(ByteArrayTag.ID, (i, d) -> readByteArray(i), ByteArrayTag.class);
		put(StringTag.ID, (i, d) -> readString(i), StringTag.class);
		put(ListTag.ID, LittleEndianNBTInputStream::readListTag, ListTag.class);
		put(CompoundTag.ID, LittleEndianNBTInputStream::readCompound, CompoundTag.class);
		put(IntArrayTag.ID, (i, d) -> readIntArray(i), IntArrayTag.class);
		put(LongArrayTag.ID, (i, d) -> readLongArray(i), LongArrayTag.class);
	}

	private static void put(byte id, ExceptionBiFunction<LittleEndianNBTInputStream, Integer, ? extends Tag<?>, IOException> reader, Class<?> clazz) {
		readers.put(id, reader);
		idClassMapping.put(id, clazz);
	}

	public LittleEndianNBTInputStream(InputStream in) {
		input = new DataInputStream(in);
	}

	public LittleEndianNBTInputStream(DataInputStream in) {
		input = in;
	}

	public NamedTag readTag(int maxDepth) throws IOException {
		byte id = readByte();
		return new NamedTag(readUTF(), readTag(id, maxDepth));
	}

	public Tag<?> readRawTag(int maxDepth) throws IOException {
		byte id = readByte();
		return readTag(id, maxDepth);
	}

	private Tag<?> readTag(byte type, int maxDepth) throws IOException {
		ExceptionBiFunction<LittleEndianNBTInputStream, Integer, ? extends Tag<?>, IOException> f;
		if ((f = readers.get(type)) == null) {
			throw new IOException("invalid tag id \"" + type + "\"");
		}
		return f.accept(this, maxDepth);
	}

	private static ByteTag readByte(LittleEndianNBTInputStream in) throws IOException {
		return new ByteTag(in.readByte());
	}

	private static ShortTag readShort(LittleEndianNBTInputStream in) throws IOException {
		return new ShortTag(in.readShort());
	}

	private static IntTag readInt(LittleEndianNBTInputStream in) throws IOException {
		return new IntTag(in.readInt());
	}

	private static LongTag readLong(LittleEndianNBTInputStream in) throws IOException {
		return new LongTag(in.readLong());
	}

	private static FloatTag readFloat(LittleEndianNBTInputStream in) throws IOException {
		return new FloatTag(in.readFloat());
	}

	private static DoubleTag readDouble(LittleEndianNBTInputStream in) throws IOException {
		return new DoubleTag(in.readDouble());
	}

	private static StringTag readString(LittleEndianNBTInputStream in) throws IOException {
		return new StringTag(in.readUTF());
	}

	private static ByteArrayTag readByteArray(LittleEndianNBTInputStream in) throws IOException {
		ByteArrayTag bat = new ByteArrayTag(new byte[in.readInt()]);
		in.readFully(bat.getValue());
		return bat;
	}

	private static IntArrayTag readIntArray(LittleEndianNBTInputStream in) throws IOException {
		int l = in.readInt();
		int[] data = new int[l];
		IntArrayTag iat = new IntArrayTag(data);
		for (int i = 0; i < l; i++) {
			data[i] = in.readInt();
		}
		return iat;
	}

	private static LongArrayTag readLongArray(LittleEndianNBTInputStream in) throws IOException {
		int l = in.readInt();
		long[] data = new long[l];
		LongArrayTag iat = new LongArrayTag(data);
		for (int i = 0; i < l; i++) {
			data[i] = in.readLong();
		}
		return iat;
	}

	private static ListTag<?> readListTag(LittleEndianNBTInputStream in, int maxDepth) throws IOException {
		byte listType = in.readByte();
		ListTag<?> list = ListTag.createUnchecked(idClassMapping.get(listType));
		int length = in.readInt();
		if (length < 0) {
			length = 0;
		}
		for (int i = 0; i < length; i++) {
			list.addUnchecked(in.readTag(listType, in.decrementMaxDepth(maxDepth)));
		}
		return list;
	}

	private static CompoundTag readCompound(LittleEndianNBTInputStream in, int maxDepth) throws IOException {
		CompoundTag comp = new CompoundTag();
		for (int id = in.readByte() & 0xFF; id != 0; id = in.readByte() & 0xFF) {
			String key = in.readUTF();
			Tag<?> element = in.readTag((byte) id, in.decrementMaxDepth(maxDepth));
			comp.put(key, element);
		}
		return comp;
	}

	@Override
	public void readFully(byte[] b) throws IOException {
		input.readFully(b);
	}

	@Override
	public void readFully(byte[] b, int off, int len) throws IOException {
		input.readFully(b, off, len);
	}

	@Override
	public int skipBytes(int n) throws IOException {
		return input.skipBytes(n);
	}

	@Override
	public boolean readBoolean() throws IOException {
		return input.readBoolean();
	}

	@Override
	public byte readByte() throws IOException {
		return input.readByte();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		return input.readUnsignedByte();
	}

	@Override
	public short readShort() throws IOException {
		return Short.reverseBytes(input.readShort());
	}

	public int readUnsignedShort() throws IOException {
		return Short.toUnsignedInt(Short.reverseBytes(input.readShort()));
	}

	@Override
	public char readChar() throws IOException {
		return Character.reverseBytes(input.readChar());
	}

	@Override
	public int readInt() throws IOException {
		return Integer.reverseBytes(input.readInt());
	}

	@Override
	public long readLong() throws IOException {
		return Long.reverseBytes(input.readLong());
	}

	@Override
	public float readFloat() throws IOException {
		return Float.intBitsToFloat(Integer.reverseBytes(input.readInt()));
	}

	@Override
	public double readDouble() throws IOException {
		return Double.longBitsToDouble(Long.reverseBytes(input.readLong()));
	}

	@Override
	@Deprecated
	public String readLine() throws IOException {
		return input.readLine();
	}

	@Override
	public void close() throws IOException {
		input.close();
	}

	@Override
	public String readUTF() throws IOException {
		byte[] bytes = new byte[readUnsignedShort()];
		readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\LittleEndianNBTOutputStream.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.myfrank.stationbuilder.schematic4j.nbt.ExceptionTriConsumer;
import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class LittleEndianNBTOutputStream implements DataOutput, NBTOutput, MaxDepthIO, Closeable {

	private final DataOutputStream output;

	private static Map<Byte, ExceptionTriConsumer<LittleEndianNBTOutputStream, Tag<?>, Integer, IOException>> writers = new HashMap<>();
	private static Map<Class<?>, Byte> classIdMapping = new HashMap<>();

	static {
		put(EndTag.ID, (o, t, d) -> {}, EndTag.class);
		put(ByteTag.ID, (o, t, d) -> writeByte(o, t), ByteTag.class);
		put(ShortTag.ID, (o, t, d) -> writeShort(o, t), ShortTag.class);
		put(IntTag.ID, (o, t, d) -> writeInt(o, t), IntTag.class);
		put(LongTag.ID, (o, t, d) -> writeLong(o, t), LongTag.class);
		put(FloatTag.ID, (o, t, d) -> writeFloat(o, t), FloatTag.class);
		put(DoubleTag.ID, (o, t, d) -> writeDouble(o, t), DoubleTag.class);
		put(ByteArrayTag.ID, (o, t, d) -> writeByteArray(o, t), ByteArrayTag.class);
		put(StringTag.ID, (o, t, d) -> writeString(o, t), StringTag.class);
		put(ListTag.ID, LittleEndianNBTOutputStream::writeList, ListTag.class);
		put(CompoundTag.ID, LittleEndianNBTOutputStream::writeCompound, CompoundTag.class);
		put(IntArrayTag.ID, (o, t, d) -> writeIntArray(o, t), IntArrayTag.class);
		put(LongArrayTag.ID, (o, t, d) -> writeLongArray(o, t), LongArrayTag.class);
	}

	private static void put(byte id, ExceptionTriConsumer<LittleEndianNBTOutputStream, Tag<?>, Integer, IOException> f, Class<?> clazz) {
		writers.put(id, f);
		classIdMapping.put(clazz, id);
	}

	public LittleEndianNBTOutputStream(OutputStream out) {
		output = new DataOutputStream(out);
	}

	public LittleEndianNBTOutputStream(DataOutputStream out) {
		output = out;
	}

	public void writeTag(NamedTag tag, int maxDepth) throws IOException {
		writeByte(tag.getTag().getID());
		if (tag.getTag().getID() != 0) {
			writeUTF(tag.getName() == null ? "" : tag.getName());
		}
		writeRawTag(tag.getTag(), maxDepth);
	}

	public void writeTag(Tag<?> tag, int maxDepth) throws IOException {
		writeByte(tag.getID());
		if (tag.getID() != 0) {
			writeUTF("");
		}
		writeRawTag(tag, maxDepth);
	}

	public void writeRawTag(Tag<?> tag, int maxDepth) throws IOException {
		ExceptionTriConsumer<LittleEndianNBTOutputStream, Tag<?>, Integer, IOException> f;
		if ((f = writers.get(tag.getID())) == null) {
			throw new IOException("invalid tag \"" + tag.getID() + "\"");
		}
		f.accept(this, tag, maxDepth);
	}

	static byte idFromClass(Class<?> clazz) {
		Byte id = classIdMapping.get(clazz);
		if (id == null) {
			throw new IllegalArgumentException("unknown Tag class " + clazz.getName());
		}
		return id;
	}

	private static void writeByte(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeByte(((ByteTag) tag).asByte());
	}
	
	private static void writeShort(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeShort(((ShortTag) tag).asShort());
	}
	
	private static void writeInt(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((IntTag) tag).asInt());
	}

	private static void writeLong(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeLong(((LongTag) tag).asLong());
	}

	private static void writeFloat(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeFloat(((FloatTag) tag).asFloat());
	}

	private static void writeDouble(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeDouble(((DoubleTag) tag).asDouble());
	}

	private static void writeString(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeUTF(((StringTag) tag).getValue());
	}

	private static void writeByteArray(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((ByteArrayTag) tag).length());
		out.write(((ByteArrayTag) tag).getValue());
	}

	private static void writeIntArray(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((IntArrayTag) tag).length());
		for (int i : ((IntArrayTag) tag).getValue()) {
			out.writeInt(i);
		}
	}

	private static void writeLongArray(LittleEndianNBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((LongArrayTag) tag).length());
		for (long l : ((LongArrayTag) tag).getValue()) {
			out.writeLong(l);
		}
	}

	private static void writeList(LittleEndianNBTOutputStream out, Tag<?> tag, int maxDepth) throws IOException {
		out.writeByte(idFromClass(((ListTag<?>) tag).getTypeClass()));
		out.writeInt(((ListTag<?>) tag).size());
		for (Tag<?> t : ((ListTag<?>) tag)) {
			out.writeRawTag(t, out.decrementMaxDepth(maxDepth));
		}
	}

	private static void writeCompound(LittleEndianNBTOutputStream out, Tag<?> tag, int maxDepth) throws IOException {
		for (Map.Entry<String, Tag<?>> entry : (CompoundTag) tag) {
			if (entry.getValue().getID() == 0) {
				throw new IOException("end tag not allowed");
			}
			out.writeByte(entry.getValue().getID());
			out.writeUTF(entry.getKey());
			out.writeRawTag(entry.getValue(), out.decrementMaxDepth(maxDepth));
		}
		out.writeByte(0);
	}

	@Override
	public void close() throws IOException {
		output.close();
	}

	@Override
	public void flush() throws IOException {
		output.flush();
	}

	@Override
	public void write(int b) throws IOException {
		output.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		output.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		output.write(b, off, len);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		output.writeBoolean(v);
	}

	@Override
	public void writeByte(int v) throws IOException {
		output.writeByte(v);
	}

	@Override
	public void writeShort(int v) throws IOException {
		output.writeShort(Short.reverseBytes((short) v));
	}

	@Override
	public void writeChar(int v) throws IOException {
		output.writeChar(Character.reverseBytes((char) v));
	}

	@Override
	public void writeInt(int v) throws IOException {
		output.writeInt(Integer.reverseBytes(v));
	}

	@Override
	public void writeLong(long v) throws IOException {
		output.writeLong(Long.reverseBytes(v));
	}

	@Override
	public void writeFloat(float v) throws IOException {
		output.writeInt(Integer.reverseBytes(Float.floatToIntBits(v)));
	}

	@Override
	public void writeDouble(double v) throws IOException {
		output.writeLong(Long.reverseBytes(Double.doubleToLongBits(v)));
	}

	@Override
	public void writeBytes(String s) throws IOException {
		output.writeBytes(s);
	}

	@Override
	public void writeChars(String s) throws IOException {
		output.writeChars(s);
	}

	@Override
	public void writeUTF(String s) throws IOException {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		writeShort(bytes.length);
		write(bytes);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NamedTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * A named tag.
 */
public class NamedTag {

	/**
	 * The name of the named tag.
	 */
	private String name;

	/**
	 * The inner tag.
	 */
	private Tag<?> tag;

	public NamedTag(String name, Tag<?> tag) {
		this.name = name;
		this.tag = tag;
	}

	/**
	 * Set a new name.
	 *
	 * @param name The new name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Set a new tag.
	 *
	 * @param tag The new tag
	 */
	public void setTag(Tag<?> tag) {
		this.tag = tag;
	}

	/**
	 * Get the named tag name.
	 *
	 * @return The named tag name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the named tag inner tag.
	 *
	 * @return The named tag inner tag
	 */
	public Tag<?> getTag() {
		return tag;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTDeserializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import cn.myfrank.stationbuilder.schematic4j.nbt.Deserializer;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class NBTDeserializer implements Deserializer<NamedTag> {

	private boolean compressed, littleEndian;

	public NBTDeserializer() {
		this(true);
	}

	public NBTDeserializer(boolean compressed) {
		this.compressed = compressed;
	}

	public NBTDeserializer(boolean compressed, boolean littleEndian) {
		this.compressed = compressed;
		this.littleEndian = littleEndian;
	}

	@Override
	public NamedTag fromStream(InputStream stream) throws IOException {
		NBTInput nbtIn;
		InputStream input;
		if (compressed) {
			input = new GZIPInputStream(stream);
		} else {
			input = stream;
		}

		if (littleEndian) {
			nbtIn = new LittleEndianNBTInputStream(input);
		} else {
			nbtIn = new NBTInputStream(input);
		}
		return nbtIn.readTag(Tag.DEFAULT_MAX_DEPTH);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTInput.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * A generic NBT input
 */
public interface NBTInput {

	/**
	 * Read a named tag from the input.
	 *
	 * @param maxDepth Maximum depth before failing deserialization
	 * @return The named tag read
	 * @throws IOException In case of error reading from the input
	 */
	NamedTag readTag(int maxDepth) throws IOException;

	/**
	 * Read a tag from the input.
	 *
	 * @param maxDepth Maximum depth before failing deserialization
	 * @return The tag read
	 * @throws IOException In case of error reading from the input
	 */
	Tag<?> readRawTag(int maxDepth) throws IOException;
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTInputStream.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import cn.myfrank.stationbuilder.schematic4j.nbt.ExceptionBiFunction;
import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class NBTInputStream extends DataInputStream implements NBTInput, MaxDepthIO {

	private static Map<Byte, ExceptionBiFunction<NBTInputStream, Integer, ? extends Tag<?>, IOException>> readers = new HashMap<>();
	private static Map<Byte, Class<?>> idClassMapping = new HashMap<>();

	static {
		put(EndTag.ID, (i, d) -> EndTag.INSTANCE, EndTag.class);
		put(ByteTag.ID, (i, d) -> readByte(i), ByteTag.class);
		put(ShortTag.ID, (i, d) -> readShort(i), ShortTag.class);
		put(IntTag.ID, (i, d) -> readInt(i), IntTag.class);
		put(LongTag.ID, (i, d) -> readLong(i), LongTag.class);
		put(FloatTag.ID, (i, d) -> readFloat(i), FloatTag.class);
		put(DoubleTag.ID, (i, d) -> readDouble(i), DoubleTag.class);
		put(ByteArrayTag.ID, (i, d) -> readByteArray(i), ByteArrayTag.class);
		put(StringTag.ID, (i, d) -> readString(i), StringTag.class);
		put(ListTag.ID, NBTInputStream::readListTag, ListTag.class);
		put(CompoundTag.ID, NBTInputStream::readCompound, CompoundTag.class);
		put(IntArrayTag.ID, (i, d) -> readIntArray(i), IntArrayTag.class);
		put(LongArrayTag.ID, (i, d) -> readLongArray(i), LongArrayTag.class);
	}

	private static void put(byte id, ExceptionBiFunction<NBTInputStream, Integer, ? extends Tag<?>, IOException> reader, Class<?> clazz) {
		readers.put(id, reader);
		idClassMapping.put(id, clazz);
	}

	public NBTInputStream(InputStream in) {
		super(in);
	}

	public NamedTag readTag(int maxDepth) throws IOException {
		byte id = readByte();
		return new NamedTag(readUTF(), readTag(id, maxDepth));
	}

	public Tag<?> readRawTag(int maxDepth) throws IOException {
		byte id = readByte();
		return readTag(id, maxDepth);
	}

	private Tag<?> readTag(byte type, int maxDepth) throws IOException {
		ExceptionBiFunction<NBTInputStream, Integer, ? extends Tag<?>, IOException> f;
		if ((f = readers.get(type)) == null) {
			throw new IOException("invalid tag id \"" + type + "\"");
		}
		return f.accept(this, maxDepth);
	}

	private static ByteTag readByte(NBTInputStream in) throws IOException {
		return new ByteTag(in.readByte());
	}

	private static ShortTag readShort(NBTInputStream in) throws IOException {
		return new ShortTag(in.readShort());
	}

	private static IntTag readInt(NBTInputStream in) throws IOException {
		return new IntTag(in.readInt());
	}

	private static LongTag readLong(NBTInputStream in) throws IOException {
		return new LongTag(in.readLong());
	}

	private static FloatTag readFloat(NBTInputStream in) throws IOException {
		return new FloatTag(in.readFloat());
	}

	private static DoubleTag readDouble(NBTInputStream in) throws IOException {
		return new DoubleTag(in.readDouble());
	}

	private static StringTag readString(NBTInputStream in) throws IOException {
		return new StringTag(in.readUTF());
	}

	private static ByteArrayTag readByteArray(NBTInputStream in) throws IOException {
		ByteArrayTag bat = new ByteArrayTag(new byte[in.readInt()]);
		in.readFully(bat.getValue());
		return bat;
	}

	private static IntArrayTag readIntArray(NBTInputStream in) throws IOException {
		int l = in.readInt();
		int[] data = new int[l];
		IntArrayTag iat = new IntArrayTag(data);
		for (int i = 0; i < l; i++) {
			data[i] = in.readInt();
		}
		return iat;
	}

	private static LongArrayTag readLongArray(NBTInputStream in) throws IOException {
		int l = in.readInt();
		long[] data = new long[l];
		LongArrayTag iat = new LongArrayTag(data);
		for (int i = 0; i < l; i++) {
			data[i] = in.readLong();
		}
		return iat;
	}

	private static ListTag<?> readListTag(NBTInputStream in, int maxDepth) throws IOException {
		byte listType = in.readByte();
		ListTag<?> list = ListTag.createUnchecked(idClassMapping.get(listType));
		int length = in.readInt();
		if (length < 0) {
			length = 0;
		}
		for (int i = 0; i < length; i++) {
			list.addUnchecked(in.readTag(listType, in.decrementMaxDepth(maxDepth)));
		}
		return list;
	}

	private static CompoundTag readCompound(NBTInputStream in, int maxDepth) throws IOException {
		CompoundTag comp = new CompoundTag();
		for (int id = in.readByte() & 0xFF; id != 0; id = in.readByte() & 0xFF) {
			String key = in.readUTF();
			Tag<?> element = in.readTag((byte) id, in.decrementMaxDepth(maxDepth));
			comp.put(key, element);
		}
		return comp;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTOutput.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public interface NBTOutput {

	void writeTag(NamedTag tag, int maxDepth) throws IOException;

	void writeTag(Tag<?> tag, int maxDepth) throws IOException;

	void flush() throws IOException;
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTOutputStream.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import cn.myfrank.stationbuilder.schematic4j.nbt.ExceptionTriConsumer;
import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class NBTOutputStream extends DataOutputStream implements NBTOutput, MaxDepthIO {

	private static Map<Byte, ExceptionTriConsumer<NBTOutputStream, Tag<?>, Integer, IOException>> writers = new HashMap<>();
	private static Map<Class<?>, Byte> classIdMapping = new HashMap<>();

	static {
		put(EndTag.ID, (o, t, d) -> {}, EndTag.class);
		put(ByteTag.ID, (o, t, d) -> writeByte(o, t), ByteTag.class);
		put(ShortTag.ID, (o, t, d) -> writeShort(o, t), ShortTag.class);
		put(IntTag.ID, (o, t, d) -> writeInt(o, t), IntTag.class);
		put(LongTag.ID, (o, t, d) -> writeLong(o, t), LongTag.class);
		put(FloatTag.ID, (o, t, d) -> writeFloat(o, t), FloatTag.class);
		put(DoubleTag.ID, (o, t, d) -> writeDouble(o, t), DoubleTag.class);
		put(ByteArrayTag.ID, (o, t, d) -> writeByteArray(o, t), ByteArrayTag.class);
		put(StringTag.ID, (o, t, d) -> writeString(o, t), StringTag.class);
		put(ListTag.ID, NBTOutputStream::writeList, ListTag.class);
		put(CompoundTag.ID, NBTOutputStream::writeCompound, CompoundTag.class);
		put(IntArrayTag.ID, (o, t, d) -> writeIntArray(o, t), IntArrayTag.class);
		put(LongArrayTag.ID, (o, t, d) -> writeLongArray(o, t), LongArrayTag.class);
	}

	private static void put(byte id, ExceptionTriConsumer<NBTOutputStream, Tag<?>, Integer, IOException> f, Class<?> clazz) {
		writers.put(id, f);
		classIdMapping.put(clazz, id);
	}

	public NBTOutputStream(OutputStream out) {
		super(out);
	}

	public void writeTag(NamedTag tag, int maxDepth) throws IOException {
		writeByte(tag.getTag().getID());
		if (tag.getTag().getID() != 0) {
			writeUTF(tag.getName() == null ? "" : tag.getName());
		}
		writeRawTag(tag.getTag(), maxDepth);
	}

	public void writeTag(Tag<?> tag, int maxDepth) throws IOException {
		writeByte(tag.getID());
		if (tag.getID() != 0) {
			writeUTF("");
		}
		writeRawTag(tag, maxDepth);
	}

	public void writeRawTag(Tag<?> tag, int maxDepth) throws IOException {
		ExceptionTriConsumer<NBTOutputStream, Tag<?>, Integer, IOException> f;
		if ((f = writers.get(tag.getID())) == null) {
			throw new IOException("invalid tag \"" + tag.getID() + "\"");
		}
		f.accept(this, tag, maxDepth);
	}

	static byte idFromClass(Class<?> clazz) {
		Byte id = classIdMapping.get(clazz);
		if (id == null) {
			throw new IllegalArgumentException("unknown Tag class " + clazz.getName());
		}
		return id;
	}

	private static void writeByte(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeByte(((ByteTag) tag).asByte());
	}
	
	private static void writeShort(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeShort(((ShortTag) tag).asShort());
	}
	
	private static void writeInt(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((IntTag) tag).asInt());
	}

	private static void writeLong(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeLong(((LongTag) tag).asLong());
	}

	private static void writeFloat(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeFloat(((FloatTag) tag).asFloat());
	}

	private static void writeDouble(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeDouble(((DoubleTag) tag).asDouble());
	}

	private static void writeString(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeUTF(((StringTag) tag).getValue());
	}

	private static void writeByteArray(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((ByteArrayTag) tag).length());
		out.write(((ByteArrayTag) tag).getValue());
	}

	private static void writeIntArray(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((IntArrayTag) tag).length());
		for (int i : ((IntArrayTag) tag).getValue()) {
			out.writeInt(i);
		}
	}

	private static void writeLongArray(NBTOutputStream out, Tag<?> tag) throws IOException {
		out.writeInt(((LongArrayTag) tag).length());
		for (long l : ((LongArrayTag) tag).getValue()) {
			out.writeLong(l);
		}
	}

	private static void writeList(NBTOutputStream out, Tag<?> tag, int maxDepth) throws IOException {
		out.writeByte(idFromClass(((ListTag<?>) tag).getTypeClass()));
		out.writeInt(((ListTag<?>) tag).size());
		for (Tag<?> t : ((ListTag<?>) tag)) {
			out.writeRawTag(t, out.decrementMaxDepth(maxDepth));
		}
	}

	private static void writeCompound(NBTOutputStream out, Tag<?> tag, int maxDepth) throws IOException {
		for (Map.Entry<String, Tag<?>> entry : (CompoundTag) tag) {
			if (entry.getValue().getID() == 0) {
				throw new IOException("end tag not allowed");
			}
			out.writeByte(entry.getValue().getID());
			out.writeUTF(entry.getKey());
			out.writeRawTag(entry.getValue(), out.decrementMaxDepth(maxDepth));
		}
		out.writeByte(0);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTSerializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import cn.myfrank.stationbuilder.schematic4j.nbt.Serializer;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class NBTSerializer implements Serializer<NamedTag> {

	private boolean compressed, littleEndian;

	public NBTSerializer() {
		this(true);
	}

	public NBTSerializer(boolean compressed) {
		this.compressed = compressed;
	}

	public NBTSerializer(boolean compressed, boolean littleEndian) {
		this.compressed = compressed;
		this.littleEndian = littleEndian;
	}

	@Override
	public void toStream(NamedTag object, OutputStream out) throws IOException {
		NBTOutput nbtOut;
		OutputStream output;
		if (compressed) {
			output = new GZIPOutputStream(out, true);
		} else {
			output = out;
		}

		if (littleEndian) {
			nbtOut = new LittleEndianNBTOutputStream(output);
		} else {
			nbtOut = new NBTOutputStream(output);
		}
		nbtOut.writeTag(object, Tag.DEFAULT_MAX_DEPTH);
		nbtOut.flush();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\NBTUtil.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public final class NBTUtil {

	private NBTUtil() {
	}

	/**
	 * Writer helper that follows the builder pattern.
	 * <p>
	 * Usage example:
	 * <pre>{@code Writer.write(nbtTag)
	 *     .littleEndian()
	 *     .compressed(false)
	 *     .to("file.schematic")}</pre>
	 */
	public static class Writer {

		public final NamedTag tag;
		private boolean compressed = true;
		private boolean littleEndian = false;

		private Writer(NamedTag tag) {
			this.tag = tag;
		}

		public static Writer write(NamedTag tag) {
			return new Writer(tag);
		}

		public static Writer write(Tag<?> tag) {
			return new Writer(new NamedTag(null, tag));
		}

		/**
		 * Toggle compression for the output. GZIP compression is used.
		 *
		 * @param compressed Whether the output should be compressed or not
		 * @return The writer builder
		 */
		public Writer compressed(boolean compressed) {
			this.compressed = compressed;
			return this;
		}

		/**
		 * Write to the output as Little Endian. Usually reserved for network packets or some systems architectures.
		 *
		 * @return the writer builder
		 */
		public Writer littleEndian() {
			this.littleEndian = true;
			return this;
		}

		/**
		 * Write to the output as Big Endian. This is the default.
		 *
		 * @return the writer builder
		 */
		public Writer bigEndian() {
			this.littleEndian = false;
			return this;
		}

		/**
		 * Writes the NBT tag to an output stream. Terminal operator.
		 *
		 * @param os The output stream to write to
		 * @throws IOException In case of error writing to the output stream
		 */
		public void to(OutputStream os) throws IOException {
			if (tag == null)
				throw new IllegalStateException("tag must be set");
			if (os == null)
				throw new IllegalStateException("output must be set");

			new NBTSerializer(compressed, littleEndian).toStream(tag, os);
		}

		/**
		 * Writes the NBT tag to a file. Terminal operator.
		 *
		 * @param path The file path fo write to
		 * @throws IOException In case of error writing to the file
		 */
		public void to(Path path) throws IOException {
			try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(path))) {
				to(os);
			}
		}

		/**
		 * Writes the NBT tag to a file. Terminal operator.
		 *
		 * @param file The file path fo write to
		 * @throws IOException In case of error writing to the file
		 */
		public void to(File file) throws IOException {
			to(file.toPath());
		}

		/**
		 * Writes the NBT tag to a file. Terminal operator.
		 *
		 * @param file The file path fo write to
		 * @throws IOException In case of error writing to the file
		 */
		public void to(String file) throws IOException {
			to(Paths.get(file));
		}
	}

	/**
	 * Reader helper that follows the builder pattern.
	 * <p>
	 * Usage example:
	 * <pre>{@code Reader.read()
	 *     .littleEndian()
	 *     .from("file.schematic")}</pre>
	 */
	public static class Reader {

		private boolean littleEndian = false;

		public Reader() {
		}

		public static Reader read() {
			return new Reader();
		}

		/**
		 * Read from the source as Little Endian. Usually reserved for network packets or some systems architectures.
		 *
		 * @return the reader builder
		 */
		public Reader littleEndian() {
			this.littleEndian = true;
			return this;
		}

		/**
		 * Read from the source as Big Endian. This is the default.
		 *
		 * @return the reader builder
		 */
		public Reader bigEndian() {
			this.littleEndian = false;
			return this;
		}

		/**
		 * Reads the NBT tag from an input stream. Terminal operator.
		 *
		 * @param is The input stream to read from
		 * @return The parsed NBT tag
		 * @throws IOException In case of error reading from the input stream
		 */
		public NamedTag from(InputStream is) throws IOException {
			return new NBTDeserializer(false/* ignored, will autodetect compression */, littleEndian)
					.fromStream(detectDecompression(is));
		}

		/**
		 * Reads the NBT tag from a byte array. Terminal operator.
		 *
		 * @param bytes The byte array
		 * @return The parsed NBT tag
		 * @throws IOException In case of error reading from the input stream
		 */
		public NamedTag from(byte[] bytes) throws IOException {
			return from(new ByteArrayInputStream(bytes));
		}

		/**
		 * Reads the NBT tag from a file. Terminal operator.
		 *
		 * @param path The file path to read from
		 * @return The parsed NBT tag
		 * @throws IOException In case of error reading from the file
		 */
		public NamedTag from(Path path) throws IOException {
			try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
				return from(is);
			}
		}

		/**
		 * Reads the NBT tag from a file. Terminal operator.
		 *
		 * @param file The file path to read from
		 * @return The parsed NBT tag
		 * @throws IOException In case of error reading from the file
		 */
		public NamedTag from(File file) throws IOException {
			return from(file.toPath());
		}

		/**
		 * Reads the NBT tag from a file. Terminal operator.
		 *
		 * @param file The file path to read from
		 * @return The parsed NBT tag
		 * @throws IOException In case of error reading from the file
		 */
		public NamedTag from(String file) throws IOException {
			return from(Paths.get(file));
		}

		private static InputStream detectDecompression(InputStream is) throws IOException {
			PushbackInputStream pbis = new PushbackInputStream(is, 2);
			int signature = (pbis.read() & 0xFF) + (pbis.read() << 8);
			pbis.unread(signature >> 8);
			pbis.unread(signature & 0xFF);
			return signature == GZIPInputStream.GZIP_MAGIC ? new GZIPInputStream(pbis) : pbis;
		}
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\ParseException.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;

public class ParseException extends IOException {

	public ParseException(String msg) {
		super(msg);
	}

	public ParseException(String msg, String value, int index) {
		super(msg + " at: " + formatError(value, index));
	}

	private static String formatError(String value, int index) {
		StringBuilder builder = new StringBuilder();
		int i = Math.min(value.length(), index);
		if (i > 35) {
			builder.append("...");
		}
		builder.append(value, Math.max(0, i - 35), i);
		builder.append("<--[HERE]");
		return builder.toString();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\SNBTDeserializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.stream.Collectors;

import cn.myfrank.stationbuilder.schematic4j.nbt.StringDeserializer;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class SNBTDeserializer implements StringDeserializer<Tag<?>> {

	@Override
	public Tag<?> fromReader(Reader reader) throws IOException {
		return fromReader(reader, Tag.DEFAULT_MAX_DEPTH);
	}

	public Tag<?> fromReader(Reader reader, int maxDepth) throws IOException {
		BufferedReader bufferedReader;
		if (reader instanceof BufferedReader) {
			bufferedReader = (BufferedReader) reader;
		} else {
			bufferedReader = new BufferedReader(reader);
		}
		return SNBTParser.parse(bufferedReader.lines().collect(Collectors.joining()), maxDepth);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\SNBTParser.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public final class SNBTParser implements MaxDepthIO {

	private static final Pattern
			FLOAT_LITERAL_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.?|\\d*\\.\\d+)(?:e[-+]?\\d+)?f$", Pattern.CASE_INSENSITIVE),
			DOUBLE_LITERAL_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.?|\\d*\\.\\d+)(?:e[-+]?\\d+)?d$", Pattern.CASE_INSENSITIVE),
			DOUBLE_LITERAL_NO_SUFFIX_PATTERN = Pattern.compile("^[-+]?(?:\\d+\\.|\\d*\\.\\d+)(?:e[-+]?\\d+)?$", Pattern.CASE_INSENSITIVE),
			BYTE_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+b$", Pattern.CASE_INSENSITIVE),
			SHORT_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+s$", Pattern.CASE_INSENSITIVE),
			INT_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+$", Pattern.CASE_INSENSITIVE),
			LONG_LITERAL_PATTERN = Pattern.compile("^[-+]?\\d+l$", Pattern.CASE_INSENSITIVE),
			NUMBER_PATTERN = Pattern.compile("^[-+]?\\d+$");

	private StringPointer ptr;

	private SNBTParser(String string) {
		this.ptr = new StringPointer(string);
	}

	public static Tag<?> parse(String string, int maxDepth) throws ParseException {
		SNBTParser parser = new SNBTParser(string);
		Tag<?> tag = parser.parseAnything(maxDepth);
		parser.ptr.skipWhitespace();
		if (parser.ptr.hasNext()) {
			throw parser.ptr.parseException("invalid characters after end of snbt");
		}
		return tag;
	}

	public static Tag<?> parse(String string) throws ParseException {
		return parse(string, Tag.DEFAULT_MAX_DEPTH);
	}

	private Tag<?> parseAnything(int maxDepth) throws ParseException {
		ptr.skipWhitespace();
		switch (ptr.currentChar()) {
			case '{':
				return parseCompoundTag(maxDepth);
			case '[':
				if (ptr.hasCharsLeft(2) && ptr.lookAhead(1) != '"' && ptr.lookAhead(2) == ';') {
					return parseNumArray();
				}
				return parseListTag(maxDepth);
		}
		return parseStringOrLiteral();
	}

	private Tag<?> parseStringOrLiteral() throws ParseException {
		ptr.skipWhitespace();
		if (ptr.currentChar() == '"') {
			return new StringTag(ptr.parseQuotedString());
		}
		String s = ptr.parseSimpleString();
		if (s.isEmpty()) {
			throw new ParseException("expected non empty value");
		}
		if (FLOAT_LITERAL_PATTERN.matcher(s).matches()) {
			return new FloatTag(Float.parseFloat(s.substring(0, s.length() - 1)));
		} else if (BYTE_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new ByteTag(Byte.parseByte(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("byte not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (SHORT_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new ShortTag(Short.parseShort(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("short not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (LONG_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new LongTag(Long.parseLong(s.substring(0, s.length() - 1)));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("long not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (INT_LITERAL_PATTERN.matcher(s).matches()) {
			try {
				return new IntTag(Integer.parseInt(s));
			} catch (NumberFormatException ex) {
				throw ptr.parseException("int not in range: \"" + s.substring(0, s.length() - 1) + "\"");
			}
		} else if (DOUBLE_LITERAL_PATTERN.matcher(s).matches()) {
			return new DoubleTag(Double.parseDouble(s.substring(0, s.length() - 1)));
		} else if (DOUBLE_LITERAL_NO_SUFFIX_PATTERN.matcher(s).matches()) {
			return new DoubleTag(Double.parseDouble(s));
		} else if ("true".equalsIgnoreCase(s)) {
			return new ByteTag(true);
		} else if ("false".equalsIgnoreCase(s)) {
			return new ByteTag(false);
		}
		return new StringTag(s);
	}

	private CompoundTag parseCompoundTag(int maxDepth) throws ParseException {
		ptr.expectChar('{');

		CompoundTag compoundTag = new CompoundTag();

		ptr.skipWhitespace();
		while (ptr.hasNext() && ptr.currentChar() != '}') {
			ptr.skipWhitespace();
			String key = ptr.currentChar() == '"' ? ptr.parseQuotedString() : ptr.parseSimpleString();
			if (key.isEmpty()) {
				throw new ParseException("empty keys are not allowed");
			}
			ptr.expectChar(':');

			compoundTag.put(key, parseAnything(decrementMaxDepth(maxDepth)));

			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar('}');
		return compoundTag;
	}

	private ListTag<?> parseListTag(int maxDepth) throws ParseException {
		ptr.expectChar('[');
		ptr.skipWhitespace();
		ListTag<?> list = ListTag.createUnchecked(EndTag.class);
		while (ptr.currentChar() != ']') {
			Tag<?> element = parseAnything(decrementMaxDepth(maxDepth));
			try {
				list.addUnchecked(element);
			} catch (IllegalArgumentException ex) {
				throw ptr.parseException(ex.getMessage());
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return list;
	}

	private ArrayTag<?> parseNumArray() throws ParseException {
		ptr.expectChar('[');
		char arrayType = ptr.next();
		ptr.expectChar(';');
		ptr.skipWhitespace();
		switch (arrayType) {
			case 'B':
				return parseByteArrayTag();
			case 'I':
				return parseIntArrayTag();
			case 'L':
				return parseLongArrayTag();
		}
		throw new ParseException("invalid array type '" + arrayType + "'");
	}

	private ByteArrayTag parseByteArrayTag() throws ParseException {
		List<Byte> byteList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					byteList.add(Byte.parseByte(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("byte not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid byte in ByteArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		byte[] bytes = new byte[byteList.size()];
		for (int i = 0; i < byteList.size(); i++) {
			bytes[i] = byteList.get(i);
		}
		return new ByteArrayTag(bytes);
	}

	private IntArrayTag parseIntArrayTag() throws ParseException {
		List<Integer> intList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					intList.add(Integer.parseInt(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("int not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid int in IntArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return new IntArrayTag(intList.stream().mapToInt(i -> i).toArray());
	}

	private LongArrayTag parseLongArrayTag() throws ParseException {
		List<Long> longList = new ArrayList<>();
		while (ptr.currentChar() != ']') {
			String s = ptr.parseSimpleString();
			ptr.skipWhitespace();
			if (NUMBER_PATTERN.matcher(s).matches()) {
				try {
					longList.add(Long.parseLong(s));
				} catch (NumberFormatException ex) {
					throw ptr.parseException("long not in range: \"" + s + "\"");
				}
			} else {
				throw ptr.parseException("invalid long in LongArrayTag: \"" + s + "\"");
			}
			if (!ptr.nextArrayElement()) {
				break;
			}
		}
		ptr.expectChar(']');
		return new LongArrayTag(longList.stream().mapToLong(l -> l).toArray());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\SNBTSerializer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;
import java.io.Writer;

import cn.myfrank.stationbuilder.schematic4j.nbt.StringSerializer;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class SNBTSerializer implements StringSerializer<Tag<?>> {

	@Override
	public void toWriter(Tag<?> tag, Writer writer) throws IOException {
		SNBTWriter.write(tag, writer);
	}

	public void toWriter(Tag<?> tag, Writer writer, int maxDepth) throws IOException {
		SNBTWriter.write(tag, writer, maxDepth);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\SNBTUtil.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;

import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

public class SNBTUtil {

	public static String toSNBT(Tag<?> tag) throws IOException {
		return new SNBTSerializer().toString(tag);
	}

	public static Tag<?> fromSNBT(String string) throws IOException {
		return new SNBTDeserializer().fromString(string);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\SNBTWriter.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.regex.Pattern;

import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ByteTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.CompoundTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.DoubleTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.EndTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.FloatTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.IntTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ListTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongArrayTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.LongTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.ShortTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.StringTag;
import cn.myfrank.stationbuilder.schematic4j.nbt.tag.Tag;

/**
 * SNBTWriter creates an SNBT String.
 *
 * */
public final class SNBTWriter implements MaxDepthIO {

	private static final Pattern NON_QUOTE_PATTERN = Pattern.compile("[a-zA-Z_.+\\-]+");

	private Writer writer;

	private SNBTWriter(Writer writer) {
		this.writer = writer;
	}

	public static void write(Tag<?> tag, Writer writer, int maxDepth) throws IOException {
		new SNBTWriter(writer).writeAnything(tag, maxDepth);
	}

	public static void write(Tag<?> tag, Writer writer) throws IOException {
		write(tag, writer, Tag.DEFAULT_MAX_DEPTH);
	}

	private void writeAnything(Tag<?> tag, int maxDepth) throws IOException {
		switch (tag.getID()) {
		case EndTag.ID:
			//do nothing
			break;
		case ByteTag.ID:
			writer.append(Byte.toString(((ByteTag) tag).asByte())).write('b');
			break;
		case ShortTag.ID:
			writer.append(Short.toString(((ShortTag) tag).asShort())).write('s');
			break;
		case IntTag.ID:
			writer.write(Integer.toString(((IntTag) tag).asInt()));
			break;
		case LongTag.ID:
			writer.append(Long.toString(((LongTag) tag).asLong())).write('l');
			break;
		case FloatTag.ID:
			writer.append(Float.toString(((FloatTag) tag).asFloat())).write('f');
			break;
		case DoubleTag.ID:
			writer.append(Double.toString(((DoubleTag) tag).asDouble())).write('d');
			break;
		case ByteArrayTag.ID:
			writeArray(((ByteArrayTag) tag).getValue(), ((ByteArrayTag) tag).length(), "B");
			break;
		case StringTag.ID:
			writer.write(escapeString(((StringTag) tag).getValue()));
			break;
		case ListTag.ID:
			writer.write('[');
			for (int i = 0; i < ((ListTag<?>) tag).size(); i++) {
				writer.write(i == 0 ? "" : ",");
				writeAnything(((ListTag<?>) tag).get(i), decrementMaxDepth(maxDepth));
			}
			writer.write(']');
			break;
		case CompoundTag.ID:
			writer.write('{');
			boolean first = true;
			for (Map.Entry<String, Tag<?>> entry : (CompoundTag) tag) {
				writer.write(first ? "" : ",");
				writer.append(escapeString(entry.getKey())).write(':');
				writeAnything(entry.getValue(), decrementMaxDepth(maxDepth));
				first = false;
			}
			writer.write('}');
			break;
		case IntArrayTag.ID:
			writeArray(((IntArrayTag) tag).getValue(), ((IntArrayTag) tag).length(), "I");
			break;
		case LongArrayTag.ID:
			writeArray(((LongArrayTag) tag).getValue(), ((LongArrayTag) tag).length(), "L");
			break;
		default:
			throw new IOException("unknown tag with id \"" + tag.getID() + "\"");
		}
	}

	private void writeArray(Object array, int length, String prefix) throws IOException {
		writer.append('[').append(prefix).write(';');
		for (int i = 0; i < length; i++) {
			writer.append(i == 0 ? "" : ",").write(Array.get(array, i).toString());
		}
		writer.write(']');
	}

	public static String escapeString(String s) {
		if (!NON_QUOTE_PATTERN.matcher(s).matches()) {
			StringBuilder sb = new StringBuilder();
			sb.append('"');
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (c == '\\' || c == '"') {
					sb.append('\\');
				}
				sb.append(c);
			}
			sb.append('"');
			return sb.toString();
		}
		return s;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\io\StringPointer.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.io;

public class StringPointer {

	private String value;
	private int index;

	public StringPointer(String value) {
		this.value = value;
	}

	public String parseSimpleString() {
		int oldIndex = index;
		while (hasNext() && isSimpleChar(currentChar())) {
			index++;
		}
		return value.substring(oldIndex, index);
	}

	public String parseQuotedString() throws ParseException {
		int oldIndex = ++index; //ignore beginning quotes
		StringBuilder sb = null;
		boolean escape = false;
		while (hasNext()) {
			char c = next();
			if (escape) {
				if (c != '\\' && c != '"') {
					throw parseException("invalid escape of '" + c + "'");
				}
				escape = false;
			} else {
				if (c == '\\') { //escape
					escape = true;
					if (sb != null) {
						continue;
					}
					sb = new StringBuilder(value.substring(oldIndex, index - 1));
					continue;
				}
				if (c == '"') {
					return sb == null ? value.substring(oldIndex, index - 1) : sb.toString();
				}
			}
			if (sb != null) {
				sb.append(c);
			}
		}
		throw parseException("missing end quote");
	}

	public boolean nextArrayElement() {
		skipWhitespace();
		if (hasNext() && currentChar() == ',') {
			index++;
			skipWhitespace();
			return true;
		}
		return false;
	}

	public void expectChar(char c) throws ParseException {
		skipWhitespace();
		boolean hasNext = hasNext();
		if (hasNext && currentChar() == c) {
			index++;
			return;
		}
		throw parseException("expected '" + c + "' but got " + (hasNext ? "'" + currentChar() + "'" : "EOF"));
	}

	public void skipWhitespace() {
		while (hasNext() && Character.isWhitespace(currentChar())) {
			index++;
		}
	}

	public boolean hasNext() {
		return index < value.length();
	}

	public boolean hasCharsLeft(int num) {
		return this.index + num < value.length();
	}

	public char currentChar() {
		return value.charAt(index);
	}

	public char next() {
		return value.charAt(index++);
	}

	public void skip(int offset) {
		index += offset;
	}

	public char lookAhead(int offset) {
		return value.charAt(index + offset);
	}

	private static boolean isSimpleChar(char c) {
		return c >= 'a' && c <= 'z'
				|| c >= 'A' && c <= 'Z'
				|| c >= '0' && c <= '9'
				|| c == '-'
				|| c == '+'
				|| c == '.'
				|| c == '_';
	}

	public ParseException parseException(String msg) {
		return new ParseException(msg, value, index);
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\ArrayTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.lang.reflect.Array;

/**
 * ArrayTag is an abstract representation of any NBT array tag.
 * For implementations see {@link ByteArrayTag}, {@link IntArrayTag}, {@link LongArrayTag}.
 * @param <T> The array type.
 * */
public abstract class ArrayTag<T> extends Tag<T> {
	/**
	 * An array tag.
	 * @param value The inner array
	 */
	public ArrayTag(T value) {
		super(value);
		if (!value.getClass().isArray()) {
			throw new UnsupportedOperationException("type of array tag must be an array");
		}
	}

	/**
	 * Get ghe array length, or size.
	 * @return The array length
	 */
	public int length() {
		return Array.getLength(getValue());
	}

	@Override
	public T getValue() {
		return super.getValue();
	}

	@Override
	public void setValue(T value) {
		super.setValue(value);
	}

	@Override
	public String valueToString(int maxDepth) {
		return arrayToString("", "");
	}

	/**
	 * @param prefix The item prefix
	 * @param suffix The item suffix
	 * @return The generated string
	 */
	protected String arrayToString(String prefix, String suffix) {
		StringBuilder sb = new StringBuilder("[").append(prefix).append("".equals(prefix) ? "" : ";");
		for (int i = 0; i < length(); i++) {
			sb.append(i == 0 ? "" : ",").append(Array.get(getValue(), i)).append(suffix);
		}
		sb.append("]");
		return sb.toString();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\ByteArrayTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Arrays;

/**
 * A byte array NBT tag.
 */
public class ByteArrayTag extends ArrayTag<byte[]> implements Comparable<ByteArrayTag> {

	/**
	 * The byte array tag discriminator.
	 */
	public static final byte ID = 7;

	/**
	 * The default value.
	 */
	public static final byte[] ZERO_VALUE = new byte[0];

	/**
	 * An empty byte array tag.
	 */
	public ByteArrayTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A byte array tag.
	 * @param value The inner array
	 */
	public ByteArrayTag(byte[] value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && Arrays.equals(getValue(), ((ByteArrayTag) other).getValue());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getValue());
	}

	@Override
	public int compareTo(ByteArrayTag other) {
		return Integer.compare(length(), other.length());
	}

	@Override
	public ByteArrayTag clone() {
		return new ByteArrayTag(Arrays.copyOf(getValue(), length()));
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\ByteTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A byte NBT tag.
 */
public class ByteTag extends NumberTag<Byte> implements Comparable<ByteTag> {

	/**
	 * The byte tag discriminator.
	 */
	public static final byte ID = 1;

	/**
	 * The default value.
	 */
	public static final byte ZERO_VALUE = 0;

	/**
	 * A byte tag with the default value.
	 */
	public ByteTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A byte tag.
	 * @param value The inner value
	 */
	public ByteTag(byte value) {
		super(value);
	}

	/**
	 * A byte tag.
	 * @param value The inner value
	 */
	public ByteTag(boolean value) {
		super((byte) (value ? 1 : 0));
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Convert this byte into a boolean value. Values greater than zero map to true.
	 *
	 * @return {@code true} if greater than 0, {@code false} otherwise
	 */
	public boolean asBoolean() {
		return getValue() > 0;
	}

	/**
	 * Sets the inner byte value.
	 *
	 * @param value The new value
	 */
	public void setValue(byte value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asByte() == ((ByteTag) other).asByte();
	}

	@Override
	public int compareTo(ByteTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public ByteTag clone() {
		return new ByteTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\CompoundTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.NotNull;

import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;

/**
 * A compound NBT tag. Works like a map.
 */
public class CompoundTag extends Tag<Map<String, Tag<?>>> implements Iterable<Map.Entry<String, Tag<?>>>, Comparable<CompoundTag>, MaxDepthIO {

	/**
	 * The compound tag discriminator.
	 */
	public static final byte ID = 10;

	/**
	 * An empty compound tag.
	 */
	public CompoundTag() {
		super(createEmptyValue());
	}

	@Override
	public byte getID() {
		return ID;
	}

	private static Map<String, Tag<?>> createEmptyValue() {
		return new HashMap<>(8);
	}

	/**
	 * Get the number of entries.
	 *
	 * @return The number of entries
	 */
	public int size() {
		return getValue().size();
	}

	/**
	 * Check if this compound tag is empty.
	 *
	 * @return Whether this compound tag is empty
	 */
	public boolean isEmpty() {
		return getValue().isEmpty();
	}

	/**
	 * Removes an entry by key.
	 *
	 * @param key The entry key to remove
	 * @return The removed entry
	 */
	public Tag<?> remove(String key) {
		return getValue().remove(key);
	}

	/**
	 * Remove all entries.
	 */
	public void clear() {
		getValue().clear();
	}

	/**
	 * Returns true if this compound tag contains a mapping for the specified key.
	 *
	 * @param key key whose presence in this map is to be tested
	 * @return {@code true} if this map contains a mapping for the specified key
	 */
	public boolean containsKey(String key) {
		return getValue().containsKey(key);
	}

	/**
	 * Returns true if this map maps one or more keys to the specified value. More formally, returns true if and only
	 * if this map contains at least one mapping to a value v such that (value==null ? v==null : value.equals(v)).
	 *
	 * @param value value whose presence in this map is to be tested
	 * @return true if this map maps one or more keys to the specified value
	 */
	public boolean containsValue(Tag<?> value) {
		return getValue().containsValue(value);
	}

	/**
	 * Returns a Collection view of the values contained in this compound tag.
	 *
	 * @return a collection view of the values contained in this compound tag
	 */
	public Collection<Tag<?>> values() {
		return getValue().values();
	}

	/**
	 * Returns a Set view of the keys contained in this compound tag.
	 *
	 * @return a set view of the keys contained in this compound tag
	 */
	public Set<String> keySet() {
		return getValue().keySet();
	}

	/**
	 * Returns a Set view of the mappings contained in this compound tag.
	 *
	 * @return a set view of the mappings contained in this compound tag
	 */
	public Set<Map.Entry<String, Tag<?>>> entrySet() {
		return new NonNullEntrySet<>(getValue().entrySet());
	}

	@Override
	public @NotNull Iterator<Map.Entry<String, Tag<?>>> iterator() {
		return entrySet().iterator();
	}

	/**
	 * Performs the given action for each entry in this map until all entries have been processed or the action throws an exception.
	 *
	 * @param action The action to be performed for each entry
	 */
	public void forEach(BiConsumer<String, Tag<?>> action) {
		getValue().forEach(action);
	}

	/**
	 * Returns the value to which the specified key is mapped coerced into a type, or null if this map contains no
	 * mapping for the key.
	 *
	 * @param key the key whose associated value is to be returned
	 * @param type the type of the value
	 * @param <C> the type of the value
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public <C extends Tag<?>> C get(String key, Class<C> type) {
		Tag<?> t = getValue().get(key);
		if (t != null) {
			return type.cast(t);
		}
		return null;
	}

	/**
	 * Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public Tag<?> get(String key) {
		return getValue().get(key);
	}

	/**
	 * Get value as a byte array tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public ByteTag getByteTag(String key) {
		return get(key, ByteTag.class);
	}

	/**
	 * Get value as a short tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public ShortTag getShortTag(String key) {
		return get(key, ShortTag.class);
	}

	/**
	 * Get value as an int tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public IntTag getIntTag(String key) {
		return get(key, IntTag.class);
	}

	/**
	 * Get value as a long tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public LongTag getLongTag(String key) {
		return get(key, LongTag.class);
	}

	/**
	 * Get value as a float tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public FloatTag getFloatTag(String key) {
		return get(key, FloatTag.class);
	}

	/**
	 * Get value as a double tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public DoubleTag getDoubleTag(String key) {
		return get(key, DoubleTag.class);
	}

	/**
	 * Get value as a string tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public StringTag getStringTag(String key) {
		return get(key, StringTag.class);
	}

	/**
	 * Get value as a byte array tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public ByteArrayTag getByteArrayTag(String key) {
		return get(key, ByteArrayTag.class);
	}

	/**
	 * Get value as an int array tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public IntArrayTag getIntArrayTag(String key) {
		return get(key, IntArrayTag.class);
	}

	/**
	 * Get value as a long array tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public LongArrayTag getLongArrayTag(String key) {
		return get(key, LongArrayTag.class);
	}

	/**
	 * Get value as a list tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public ListTag<?> getListTag(String key) {
		return get(key, ListTag.class);
	}

	/**
	 * Get value as a compound tag.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public CompoundTag getCompoundTag(String key) {
		return get(key, CompoundTag.class);
	}

	/**
	 * Get value as a boolean.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public boolean getBoolean(String key) {
		Tag<?> t = get(key);
		return t instanceof ByteTag && ((ByteTag) t).asBoolean();
	}

	/**
	 * Get value as a byte.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public byte getByte(String key) {
		ByteTag t = getByteTag(key);
		return t == null ? ByteTag.ZERO_VALUE : t.asByte();
	}

	/**
	 * Get value as a short.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public short getShort(String key) {
		ShortTag t = getShortTag(key);
		return t == null ? ShortTag.ZERO_VALUE : t.asShort();
	}

	/**
	 * Get value as an int.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public int getInt(String key) {
		IntTag t = getIntTag(key);
		return t == null ? IntTag.ZERO_VALUE : t.asInt();
	}

	/**
	 * Get value as a long.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public long getLong(String key) {
		LongTag t = getLongTag(key);
		return t == null ? LongTag.ZERO_VALUE : t.asLong();
	}

	/**
	 * Get value as a float.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public float getFloat(String key) {
		FloatTag t = getFloatTag(key);
		return t == null ? FloatTag.ZERO_VALUE : t.asFloat();
	}

	/**
	 * Get value as a double.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public double getDouble(String key) {
		DoubleTag t = getDoubleTag(key);
		return t == null ? DoubleTag.ZERO_VALUE : t.asDouble();
	}

	/**
	 * Get value as a string.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public String getString(String key) {
		StringTag t = getStringTag(key);
		return t == null ? StringTag.ZERO_VALUE : t.getValue();
	}

	/**
	 * Get value as a byte array.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public byte[] getByteArray(String key) {
		ByteArrayTag t = getByteArrayTag(key);
		return t == null ? ByteArrayTag.ZERO_VALUE : t.getValue();
	}

	/**
	 * Get value as an int array.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public int[] getIntArray(String key) {
		IntArrayTag t = getIntArrayTag(key);
		return t == null ? IntArrayTag.ZERO_VALUE : t.getValue();
	}

	/**
	 * Get value as a long array.
	 *
	 * @param key the key whose associated value is to be returned
	 * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
	 */
	public long[] getLongArray(String key) {
		LongArrayTag t = getLongArrayTag(key);
		return t == null ? LongArrayTag.ZERO_VALUE : t.getValue();
	}

	/**
	 * Associates the specified value with the specified key in this map (optional operation). If the map previously
	 * contained a mapping for the key, the old value is replaced by the specified value.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param tag value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> put(String key, Tag<?> tag) {
		return getValue().put(Objects.requireNonNull(key), Objects.requireNonNull(tag));
	}

	/**
	 * Inserts the boolean value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putBoolean(String key, boolean value) {
		return put(key, new ByteTag(value));
	}

	/**
	 * Inserts the byte value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putByte(String key, byte value) {
		return put(key, new ByteTag(value));
	}

	/**
	 * Inserts the short value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putShort(String key, short value) {
		return put(key, new ShortTag(value));
	}

	/**
	 * Inserts the int value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putInt(String key, int value) {
		return put(key, new IntTag(value));
	}

	/**
	 * Inserts the long value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putLong(String key, long value) {
		return put(key, new LongTag(value));
	}

	/**
	 * Inserts the float value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putFloat(String key, float value) {
		return put(key, new FloatTag(value));
	}

	/**
	 * Inserts the double value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putDouble(String key, double value) {
		return put(key, new DoubleTag(value));
	}

	/**
	 * Inserts the string value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putString(String key, String value) {
		return put(key, new StringTag(value));
	}

	/**
	 * Inserts the byte array value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putByteArray(String key, byte[] value) {
		return put(key, new ByteArrayTag(value));
	}

	/**
	 * Inserts the int array value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putIntArray(String key, int[] value) {
		return put(key, new IntArrayTag(value));
	}

	/**
	 * Inserts the long array value into this compound tag.
	 *
	 * @param key key with which the specified value is to be associated value
	 * @param value value to be associated with the specified key
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public Tag<?> putLongArray(String key, long[] value) {
		return put(key, new LongArrayTag(value));
	}

	@Override
	public String valueToString(int maxDepth) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			sb.append(first ? "" : ",")
					.append(escapeString(e.getKey(), false)).append(":")
					.append(e.getValue().toString(decrementMaxDepth(maxDepth)));
			first = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!super.equals(other) || size() != ((CompoundTag) other).size()) {
			return false;
		}
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			Tag<?> v;
			if ((v = ((CompoundTag) other).get(e.getKey())) == null || !e.getValue().equals(v)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int compareTo(CompoundTag o) {
		return Integer.compare(size(), o.getValue().size());
	}

	@Override
	public CompoundTag clone() {
		CompoundTag copy = new CompoundTag();
		for (Map.Entry<String, Tag<?>> e : getValue().entrySet()) {
			copy.put(e.getKey(), e.getValue().clone());
		}
		return copy;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\DoubleTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A double NBT tag.
 */
public class DoubleTag extends NumberTag<Double> implements Comparable<DoubleTag> {

	/**
	 * The double tag discriminator.
	 */
	public static final byte ID = 6;

	/**
	 * The default value.
	 */
	public static final double ZERO_VALUE = 0.0D;

	/**
	 * A double tag with the default value.
	 */
	public DoubleTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A double tag.
	 *
	 * @param value The inner value
	 */
	public DoubleTag(double value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Set a new value.
	 *
	 * @param value The new value
	 */
	public void setValue(double value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && getValue().equals(((DoubleTag) other).getValue());
	}

	@Override
	public int compareTo(DoubleTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public DoubleTag clone() {
		return new DoubleTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\EndTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * An end NBT tag. Used to represent the lack of value, like a {@code null}.
 */
public final class EndTag extends Tag<Void> {

	/**
	 * The end tag discriminator.
	 */
	public static final byte ID = 0;

	/**
	 * The default value.
	 */
	public static final EndTag INSTANCE = new EndTag();

	/**
	 * An end tag.
	 */
	private EndTag() {
		super(null);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	protected Void checkValue(Void value) {
		return value;
	}

	@Override
	public String valueToString(int maxDepth) {
		return "\"end\"";
	}

	@Override
	public EndTag clone() {
		return INSTANCE;
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\FloatTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A float NBT tag.
 */
public class FloatTag extends NumberTag<Float> implements Comparable<FloatTag> {

	/**
	 * The float tag discriminator.
	 */
	public static final byte ID = 5;

	/**
	 * The default value.
	 */
	public static final float ZERO_VALUE = 0.0F;

	/**
	 * A float tag with the default value.
	 */
	public FloatTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A float tag.
	 * @param value The inner value
	 */
	public FloatTag(float value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Set a new value.
	 *
	 * @param value The new value
	 */
	public void setValue(float value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && getValue().equals(((FloatTag) other).getValue());
	}

	@Override
	public int compareTo(FloatTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public FloatTag clone() {
		return new FloatTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\IntArrayTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Arrays;

/**
 * An int array NBT tag.
 */
public class IntArrayTag extends ArrayTag<int[]> implements Comparable<IntArrayTag> {

	/**
	 * The int array tag discriminator.
	 */
	public static final byte ID = 11;

	/**
	 * The default value.
	 */
	public static final int[] ZERO_VALUE = new int[0];

	/**
	 * An empty int array tag.
	 */
	public IntArrayTag() {
		super(ZERO_VALUE);
	}

	/**
	 * An int array tag.
	 * @param value The inner value
	 */
	public IntArrayTag(int[] value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && Arrays.equals(getValue(), ((IntArrayTag) other).getValue());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getValue());
	}

	@Override
	public int compareTo(IntArrayTag other) {
		return Integer.compare(length(), other.length());
	}

	@Override
	public IntArrayTag clone() {
		return new IntArrayTag(Arrays.copyOf(getValue(), length()));
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\IntTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * An int NBT tag.
 */
public class IntTag extends NumberTag<Integer> implements Comparable<IntTag> {

	/**
	 * The int tag discriminator.
	 */
	public static final byte ID = 3;

	/**
	 * The default value.
	 */
	public static final int ZERO_VALUE = 0;

	/**
	 * An int tag with the default value.
	 */
	public IntTag() {
		super(ZERO_VALUE);
	}

	/**
	 * An int tag.
	 * @param value The inner value
	 */
	public IntTag(int value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Set a new value.
	 *
	 * @param value The new value
	 */
	public void setValue(int value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asInt() == ((IntTag) other).asInt();
	}

	@Override
	public int compareTo(IntTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public IntTag clone() {
		return new IntTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\ListTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthIO;

/**
 * ListTag represents a typed List in the nbt structure.
 * An empty {@link ListTag} will be of type {@link EndTag} (unknown type).
 * The type of empty untyped {@link ListTag} can be set by using any of the {@code add()}
 * methods or any of the {@code as...List()} methods.
 *
 * @param <T> The type of the list entries
 */
public class ListTag<T extends Tag<?>> extends Tag<List<T>> implements Iterable<T>, Comparable<ListTag<T>>, MaxDepthIO {

	/**
	 * The list tag discriminator.
	 */
	public static final byte ID = 9;

	/**
	 * The type of the list entries.
	 */
	private Class<?> typeClass = null;

	/**
	 * A list tag.
	 */
	private ListTag() {
		super(createEmptyValue(3));
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * <p>Creates a non-type-safe ListTag. Its element type will be set after the first
	 * element was added.</p>
	 *
	 * <p>This is an internal helper method for cases where the element type is not known
	 * at construction time. Use {@link #ListTag(Class)} when the type is known.</p>
	 *
	 * @param typeClass The type of the list entries
	 * @return A new non-type-safe ListTag
	 */
	public static ListTag<?> createUnchecked(Class<?> typeClass) {
		ListTag<?> list = new ListTag<>();
		list.typeClass = typeClass;
		return list;
	}

	/**
	 * <p>Creates an empty mutable list to be used as empty value of ListTags.</p>
	 *
	 * @param <T>             Type of the list elements
	 * @param initialCapacity The initial capacity of the returned List
	 * @return An instance of {@link java.util.List} with an initial capacity of 3
	 */
	private static <T> List<T> createEmptyValue(int initialCapacity) {
		return new ArrayList<>(initialCapacity);
	}

	/**
	 * @param typeClass The exact class of the elements
	 * @throws IllegalArgumentException When {@code typeClass} is {@link EndTag}{@code .class}
	 * @throws NullPointerException     When {@code typeClass} is {@code null}
	 */
	public ListTag(Class<? super T> typeClass) throws IllegalArgumentException, NullPointerException {
		super(createEmptyValue(3));
		if (typeClass == EndTag.class) {
			throw new IllegalArgumentException("cannot create ListTag with EndTag elements");
		}
		this.typeClass = Objects.requireNonNull(typeClass);
	}

	/**
	 * Get the type of the entries in this list.
	 *
	 * @return The type of the entries in this list
	 */
	public Class<?> getTypeClass() {
		return typeClass == null ? EndTag.class : typeClass;
	}

	/**
	 * Returns the number of elements in this list. If this list contains more than Integer.MAX_VALUE elements,
	 * returns Integer.MAX_VALUE.
	 *
	 * @return the number of elements in this list
	 */
	public int size() {
		return getValue().size();
	}

	/**
	 * Removes the element at the specified position in this list (optional operation). Shifts any subsequent elements
	 * to the left (subtracts one from their indices). Returns the element that was removed from the list.
	 *
	 * @param index the index of the element to be removed
	 * @return the element previously at the specified position
	 */
	public T remove(int index) {
		return getValue().remove(index);
	}

	/**
	 * Removes all the elements from this list (optional operation). The list will be empty after this call returns.
	 */
	public void clear() {
		getValue().clear();
	}

	/**
	 * Returns true if this list contains the specified element. More formally, returns true if and only if this list
	 * contains at least one element e such that (o==null ? e==null : o.equals(e)).
	 *
	 * @param t element whose presence in this list is to be tested
	 * @return true if this list contains the specified element
	 */
	public boolean contains(T t) {
		return getValue().contains(t);
	}

	/**
	 * Returns true if this list contains all of the elements of the specified collection.
	 *
	 * @param tags collection to be checked for containment in this list
	 * @return true if this list contains all the elements of the specified collection
	 */
	public boolean containsAll(Collection<Tag<?>> tags) {
		return getValue().containsAll(tags);
	}

	/**
	 * Sorts this list according to the order induced by the specified Comparator.
	 * <p>
	 * All elements in this list must be mutually comparable using the specified comparator (that is, c.compare(e1, e2)
	 * must not throw a ClassCastException for any elements e1 and e2 in the list).
	 * <p>
	 * If the specified comparator is null then all elements in this list must implement the Comparable interface and
	 * the elements' natural ordering should be used.
	 * <p>
	 * This list must be modifiable, but need not be resizable.
	 *
	 * @param comparator the Comparator used to compare list elements. A null value indicates that the elements' natural ordering should be used
	 */
	public void sort(Comparator<T> comparator) {
		getValue().sort(comparator);
	}

	@Override
	public @NotNull Iterator<T> iterator() {
		return getValue().iterator();
	}

	@Override
	public void forEach(Consumer<? super T> action) {
		getValue().forEach(action);
	}

	/**
	 * Replaces the element at the specified position in this list with the specified element (optional operation).
	 *
	 * @param index index of the element to replace element
	 * @param t element to be stored at the specified position
	 * @return the element previously at the specified position
	 */
	public T set(int index, T t) {
		return getValue().set(index, Objects.requireNonNull(t));
	}

	/**
	 * Adds a Tag to this ListTag after the last index.
	 *
	 * @param t The element to be added.
	 */
	public void add(T t) {
		add(size(), t);
	}

	/**
	 * Inserts the specified element at the specified position in this list (optional operation). Shifts the element
	 * currently at that position (if any) and any subsequent elements to the right (adds one to their indices).
	 *
	 * @param index index at which the specified element is to be inserted element
	 * @param t element to be inserted
	 */
	public void add(int index, T t) {
		Objects.requireNonNull(t);
		if (getTypeClass() == EndTag.class) {
			typeClass = t.getClass();
		} else if (typeClass != t.getClass()) {
			throw new ClassCastException(
					String.format("cannot add %s to ListTag<%s>",
							t.getClass().getSimpleName(),
							typeClass.getSimpleName()));
		}
		getValue().add(index, t);
	}

	/**
	 * Add all entries in collection to the list tag.
	 *
	 * @param t Entries to add
	 */
	public void addAll(Collection<T> t) {
		for (T tt : t) {
			add(tt);
		}
	}

	/**
	 * Add all entries in collection to the list tag at a specific index.
	 *
	 * @param index The index to insert the new values
	 * @param t     Entries to add
	 */
	public void addAll(int index, Collection<T> t) {
		int i = 0;
		for (T tt : t) {
			add(index + i, tt);
			i++;
		}
	}

	/**
	 * Add a boolean value.
	 *
	 * @param value The new value
	 */
	public void addBoolean(boolean value) {
		addUnchecked(new ByteTag(value));
	}

	/**
	 * Add a byte value.
	 *
	 * @param value The new value
	 */
	public void addByte(byte value) {
		addUnchecked(new ByteTag(value));
	}

	/**
	 * Add a short value.
	 *
	 * @param value The new value
	 */
	public void addShort(short value) {
		addUnchecked(new ShortTag(value));
	}

	/**
	 * Add an int value.
	 *
	 * @param value The new value
	 */
	public void addInt(int value) {
		addUnchecked(new IntTag(value));
	}

	/**
	 * Add a long value.
	 *
	 * @param value The new value
	 */
	public void addLong(long value) {
		addUnchecked(new LongTag(value));
	}

	/**
	 * Add a float value.
	 *
	 * @param value The new value
	 */
	public void addFloat(float value) {
		addUnchecked(new FloatTag(value));
	}

	/**
	 * Add a double value.
	 *
	 * @param value The new value
	 */
	public void addDouble(double value) {
		addUnchecked(new DoubleTag(value));
	}

	/**
	 * Add a string value.
	 *
	 * @param value The new value
	 */
	public void addString(String value) {
		addUnchecked(new StringTag(value));
	}

	/**
	 * Add a byte array value.
	 *
	 * @param value The new value
	 */
	public void addByteArray(byte[] value) {
		addUnchecked(new ByteArrayTag(value));
	}

	/**
	 * Add an int array value.
	 *
	 * @param value The new value
	 */
	public void addIntArray(int[] value) {
		addUnchecked(new IntArrayTag(value));
	}

	/**
	 * Add a long array value.
	 *
	 * @param value The new value
	 */
	public void addLongArray(long[] value) {
		addUnchecked(new LongArrayTag(value));
	}

	/**
	 * Returns the element at the specified position in this list.
	 *
	 * @param index index of the element to return
	 * @return the element at the specified position in this list
	 * @throws IndexOutOfBoundsException if the index is out of range (<code>index &lt; 0 || index &gt;= size()</code>)
	 */
	public T get(int index) {
		return getValue().get(index);
	}

	/**
	 * Returns the index of the first occurrence of the specified element in this list, or -1 if this list does not
	 * contain the element. More formally, returns the lowest index i such that
	 * (o==null ? get(i)==null : o.equals(get(i))), or -1 if there is no such index.
	 *
	 * @param t element to search for
	 * @return the index of the first occurrence of the specified element in this list, or -1 if this list does not contain the element
	 * @throws ClassCastException   if the type of the specified element is incompatible with this list (optional)
	 * @throws NullPointerException if the specified element is null and this list does not permit null elements (optional)
	 */
	public int indexOf(T t) {
		return getValue().indexOf(t);
	}

	/**
	 * Coerces this list tag into a specific type.
	 *
	 * @param type The type to coerce into
	 * @param <L> The type to coerce into
	 * @return The coerced list tag
	 */
	@SuppressWarnings("unchecked")
	public <L extends Tag<?>> ListTag<L> asTypedList(Class<L> type) {
		checkTypeClass(type);
		return (ListTag<L>) this;
	}

	/**
	 * Coerces this list tag into a byte tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<ByteTag> asByteTagList() {
		return asTypedList(ByteTag.class);
	}

	/**
	 * Coerces this list tag into a short tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<ShortTag> asShortTagList() {
		return asTypedList(ShortTag.class);
	}

	/**
	 * Coerces this list tag into an int tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<IntTag> asIntTagList() {
		return asTypedList(IntTag.class);
	}

	/**
	 * Coerces this list tag into a long tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<LongTag> asLongTagList() {
		return asTypedList(LongTag.class);
	}

	/**
	 * Coerces this list tag into a float tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<FloatTag> asFloatTagList() {
		return asTypedList(FloatTag.class);
	}

	/**
	 * Coerces this list tag into a double tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<DoubleTag> asDoubleTagList() {
		return asTypedList(DoubleTag.class);
	}

	/**
	 * Coerces this list tag into a string tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<StringTag> asStringTagList() {
		return asTypedList(StringTag.class);
	}

	/**
	 * Coerces this list tag into a byte array tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<ByteArrayTag> asByteArrayTagList() {
		return asTypedList(ByteArrayTag.class);
	}

	/**
	 * Coerces this list tag into an int array tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<IntArrayTag> asIntArrayTagList() {
		return asTypedList(IntArrayTag.class);
	}

	/**
	 * Coerces this list tag into a long array tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<LongArrayTag> asLongArrayTagList() {
		return asTypedList(LongArrayTag.class);
	}

	/**
	 * Coerces this list tag into a list of list tags.
	 *
	 * @return The coerced list tag
	 */
	@SuppressWarnings("unchecked")
	public ListTag<ListTag<?>> asListTagList() {
		checkTypeClass(ListTag.class);
		typeClass = ListTag.class;
		return (ListTag<ListTag<?>>) this;
	}

	/**
	 * Coerces this list tag into a compound tag list.
	 *
	 * @return The coerced list tag
	 */
	public ListTag<CompoundTag> asCompoundTagList() {
		return asTypedList(CompoundTag.class);
	}

	@Override
	public String valueToString(int maxDepth) {
		StringBuilder sb = new StringBuilder("{\"type\":\"").append(getTypeClass().getSimpleName()).append("\",\"list\":[");
		for (int i = 0; i < size(); i++) {
			sb.append(i > 0 ? "," : "").append(get(i).valueToString(decrementMaxDepth(maxDepth)));
		}
		sb.append("]}");
		return sb.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!super.equals(other) || size() != ((ListTag<?>) other).size() || getTypeClass() != ((ListTag<?>) other).getTypeClass()) {
			return false;
		}
		for (int i = 0; i < size(); i++) {
			if (!get(i).equals(((ListTag<?>) other).get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getTypeClass().hashCode(), getValue().hashCode());
	}

	@Override
	public int compareTo(ListTag<T> o) {
		return Integer.compare(size(), o.getValue().size());
	}

	@SuppressWarnings("unchecked")
	@Override
	public ListTag<T> clone() {
		ListTag<T> copy = new ListTag<>();
		// assure type safety for clone
		copy.typeClass = typeClass;
		for (T t : getValue()) {
			copy.add((T) t.clone());
		}
		return copy;
	}

	/**
	 * Inserts the specified element without confirming if it is the same type as the list.
	 *
	 * @param tag element to be inserted
	 */
	@SuppressWarnings("unchecked")
	public void addUnchecked(Tag<?> tag) {
		if (getTypeClass() != EndTag.class && typeClass != tag.getClass()) {
			throw new IllegalArgumentException(String.format(
					"cannot add %s to ListTag<%s>",
					tag.getClass().getSimpleName(), typeClass.getSimpleName()));
		}
		add(size(), (T) tag);
	}

	/**
	 * Check the type of the entries on this list tag.
	 * @param clazz The expected type
	 */
	private void checkTypeClass(Class<?> clazz) {
		if (getTypeClass() != EndTag.class && typeClass != clazz) {
			throw new ClassCastException(String.format(
					"cannot cast ListTag<%s> to ListTag<%s>",
					typeClass.getSimpleName(), clazz.getSimpleName()));
		}
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\LongArrayTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Arrays;

/**
 * A long array NBT tag.
 */
public class LongArrayTag extends ArrayTag<long[]> implements Comparable<LongArrayTag> {

	/**
	 * The long array tag discriminator.
	 */
	public static final byte ID = 12;

	/**
	 * The default value.
	 */
	public static final long[] ZERO_VALUE = new long[0];

	/**
	 * An empty long array tag.
	 */
	public LongArrayTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A long array tag.
	 * @param value The inner value
	 */
	public LongArrayTag(long[] value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && Arrays.equals(getValue(), ((LongArrayTag) other).getValue());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getValue());
	}

	@Override
	public int compareTo(LongArrayTag other) {
		return Integer.compare(length(), other.length());
	}

	@Override
	public LongArrayTag clone() {
		return new LongArrayTag(Arrays.copyOf(getValue(), length()));
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\LongTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A long NBT tag.
 */
public class LongTag extends NumberTag<Long> implements Comparable<LongTag> {

	/**
	 * The long tag discriminator.
	 */
	public static final byte ID = 4;

	/**
	 * The default value.
	 */
	public static final long ZERO_VALUE = 0L;

	/**
	 * A long tag with the default value.
	 */
	public LongTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A long tag.
	 * @param value The inner value
	 */
	public LongTag(long value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Set a new value.
	 *
	 * @param value The new value
	 */
	public void setValue(long value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asLong() == ((LongTag) other).asLong();
	}

	@Override
	public int compareTo(LongTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public LongTag clone() {
		return new LongTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\NonNullEntrySet.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * A decorator for the Set returned by CompoundTag#entrySet()
 * that disallows setting null values.
 * */
class NonNullEntrySet<K, V> implements Set<Map.Entry<K, V>> {

	/**
	 * The inner set.
	 */
	private final Set<Map.Entry<K, V>> set;

	NonNullEntrySet(Set<Map.Entry<K, V>> set) {
		this.set = set;
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return set.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return set.contains(o);
	}

	@Override
	public @NotNull Iterator<Map.Entry<K, V>> iterator() {
		return new NonNullEntrySetIterator(set.iterator());
	}

	@Override
	public Object @NotNull [] toArray() {
		return set.toArray();
	}

	@Override
	public <T> T @NotNull [] toArray(T @NotNull [] a) {
		return set.toArray(a);
	}

	@Override
	public boolean add(Map.Entry<K, V> kvEntry) {
		return set.add(kvEntry);
	}

	@Override
	public boolean remove(Object o) {
		return set.remove(o);
	}

	@Override
	public boolean containsAll(@NotNull Collection<?> c) {
		return set.containsAll(c);
	}

	@Override
	public boolean addAll(@NotNull Collection<? extends Map.Entry<K, V>> c) {
		return set.addAll(c);
	}

	@Override
	public boolean retainAll(@NotNull Collection<?> c) {
		return set.retainAll(c);
	}

	@Override
	public boolean removeAll(@NotNull Collection<?> c) {
		return set.removeAll(c);
	}

	@Override
	public void clear() {
		set.clear();
	}

	class NonNullEntrySetIterator implements Iterator<Map.Entry<K, V>> {

		private final Iterator<Map.Entry<K, V>> iterator;

		NonNullEntrySetIterator(Iterator<Map.Entry<K, V>> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Map.Entry<K, V> next() {
			return new NonNullEntry(iterator.next());
		}
	}

	class NonNullEntry implements Map.Entry<K, V> {

		private final Map.Entry<K, V> entry;

		NonNullEntry(Map.Entry<K, V> entry) {
			this.entry = entry;
		}

		@Override
		public K getKey() {
			return entry.getKey();
		}

		@Override
		public V getValue() {
			return entry.getValue();
		}

		@Override
		public V setValue(V value) {
			if (value == null) {
				throw new NullPointerException(getClass().getSimpleName() + " does not allow setting null");
			}
			return entry.setValue(value);
		}

		@Override
		public boolean equals(Object o) {
			return entry.equals(o);
		}

		@Override
		public int hashCode() {
			return entry.hashCode();
		}
	}
}
```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\NumberTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A generic numeric NBT tag.
 *
 * @see ByteTag
 * @see ShortTag
 * @see IntTag
 * @see LongTag
 * @see FloatTag
 * @see DoubleTag
 */
public abstract class NumberTag<T extends Number & Comparable<T>> extends Tag<T> {

	/**
	 * A number tag.
	 *
	 * @param value The inner value
	 */
	public NumberTag(T value) {
		super(value);
	}

	public byte asByte() {
		return getValue().byteValue();
	}

	public short asShort() {
		return getValue().shortValue();
	}

	public int asInt() {
		return getValue().intValue();
	}

	public long asLong() {
		return getValue().longValue();
	}

	public float asFloat() {
		return getValue().floatValue();
	}

	public double asDouble() {
		return getValue().doubleValue();
	}

	@Override
	public String valueToString(int maxDepth) {
		return getValue().toString();
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\ShortTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A short NBT tag.
 */
public class ShortTag extends NumberTag<Short> implements Comparable<ShortTag> {

	/**
	 * The short tag discriminator.
	 */
	public static final byte ID = 2;

	/**
	 * The default value.
	 */
	public static final short ZERO_VALUE = 0;

	/**
	 * A short tag with the default value.
	 */
	public ShortTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A short tag.
	 * @param value The inner value
	 */
	public ShortTag(short value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	/**
	 * Set a new value.
	 *
	 * @param value The new value
	 */
	public void setValue(short value) {
		super.setValue(value);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && asShort() == ((ShortTag) other).asShort();
	}

	@Override
	public int compareTo(ShortTag other) {
		return getValue().compareTo(other.getValue());
	}

	@Override
	public ShortTag clone() {
		return new ShortTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\StringTag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

/**
 * A string NBT tag.
 */
public class StringTag extends Tag<String> implements Comparable<StringTag> {

	/**
	 * The string tag discriminator.
	 */
	public static final byte ID = 8;

	/**
	 * The default value.
	 */
	public static final String ZERO_VALUE = "";

	/**
	 * An empty string tag.
	 */
	public StringTag() {
		super(ZERO_VALUE);
	}

	/**
	 * A string tag.
	 * @param value The inner value
	 */
	public StringTag(String value) {
		super(value);
	}

	@Override
	public byte getID() {
		return ID;
	}

	@Override
	public String getValue() {
		return super.getValue();
	}

	@Override
	public void setValue(String value) {
		super.setValue(value);
	}

	@Override
	public String valueToString(int maxDepth) {
		return escapeString(getValue(), false);
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other) && getValue().equals(((StringTag) other).getValue());
	}

	@Override
	public int compareTo(StringTag o) {
		return getValue().compareTo(o.getValue());
	}

	@Override
	public StringTag clone() {
		return new StringTag(getValue());
	}
}

```

## main\java\cn\myfrank\stationbuilder\schematic4j\nbt\tag\Tag.java

```java
/* Vendored version of Quertz NBT 6.1 - https://github.com/Querz/NBT */
package cn.myfrank.stationbuilder.schematic4j.nbt.tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.myfrank.stationbuilder.schematic4j.nbt.MaxDepthReachedException;

/**
 * Base class for all NBT tags.
 * 
 * <h2>Nesting</h2>
 * <p>All methods serializing instances or deserializing data track the nesting levels to prevent 
 * circular references or malicious data which could, when deserialized, result in thousands 
 * of instances causing a denial of service.</p>
 * 
 * <p>These methods have a parameter for the maximum nesting depth they are allowed to traverse. A 
 * value of {@code 0} means that only the object itself, but no nested objects may be processed. 
 * If an instance is nested further than allowed, a {@link MaxDepthReachedException} will be thrown.
 * Providing a negative maximum nesting depth will cause an {@code IllegalArgumentException} 
 * to be thrown.</p>
 * 
 * <p>Some methods do not provide a parameter to specify the maximum nesting depth, but instead use 
 * {@link #DEFAULT_MAX_DEPTH}, which is also the maximum used by Minecraft. This is documented for 
 * the respective methods.</p>
 * 
 * <p>If custom NBT tags contain objects other than NBT tags, which can be nested as well, then there 
 * is no guarantee that {@code MaxDepthReachedException}s are thrown for them. The respective class 
 * will document this behavior accordingly.</p>
 * 
 * @param <T> The type of the contained value
 * */
public abstract class Tag<T> implements Cloneable {

	/**
	 * The default maximum depth of the NBT structure.
	 * */
	public static final int DEFAULT_MAX_DEPTH = 512;

	/**
	 * Map of characters and their escaped counterparts.
	 */
	private static final Map<String, String> ESCAPE_CHARACTERS;
	static {
		final Map<String, String> temp = new HashMap<>();
		temp.put("\\", "\\\\\\\\");
		temp.put("\n", "\\\\n");
		temp.put("\t", "\\\\t");
		temp.put("\r", "\\\\r");
		temp.put("\"", "\\\\\"");
		ESCAPE_CHARACTERS = Collections.unmodifiableMap(temp);
	}

	private static final Pattern ESCAPE_PATTERN = Pattern.compile("[\\\\\n\t\r\"]");
	private static final Pattern NON_QUOTE_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-+]+");

	private T value;

	/**
	 * Initializes this Tag with some value. If the value is {@code null}, it will
	 * throw a {@code NullPointerException}
	 * @param value The value to be set for this Tag.
	 * */
	public Tag(T value) {
		setValue(value);
	}

	/**
	 * @return This Tag's ID, usually used for serialization and deserialization.
	 * */
	public abstract byte getID();

	/**
	 * @return The value of this Tag.
	 * */
	protected T getValue() {
		return value;
	}

	/**
	 * Sets the value for this Tag directly.
	 * @param value The value to be set.
	 * @throws NullPointerException If the value is null
	 * */
	protected void setValue(T value) {
		this.value = checkValue(value);
	}

	/**
	 * Checks if the value {@code value} is {@code null}.
	 * @param value The value to check
	 * @throws NullPointerException If {@code value} was {@code null}
	 * @return The parameter {@code value}
	 * */
	protected T checkValue(T value) {
		return Objects.requireNonNull(value);
	}

	/**
	 * Calls {@link Tag#toString(int)} with an initial depth of {@code 0}.
	 * @see Tag#toString(int)
	 * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
	 * */
	@Override
	public final String toString() {
		return toString(DEFAULT_MAX_DEPTH);
	}

	/**
	 * Creates a string representation of this Tag in a valid JSON format.
	 * @param maxDepth The maximum nesting depth.
	 * @return The string representation of this Tag.
	 * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
	 * */
	public String toString(int maxDepth) {
		return "{\"type\":\""+ getClass().getSimpleName() + "\"," +
				"\"value\":" + valueToString(maxDepth) + "}";
	}

	/**
	 * Calls {@link Tag#valueToString(int)} with {@link Tag#DEFAULT_MAX_DEPTH}.
	 * @return The string representation of the value of this Tag.
	 * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
	 * */
	public String valueToString() {
		return valueToString(DEFAULT_MAX_DEPTH);
	}

	/**
	 * Returns a JSON representation of the value of this Tag.
	 * @param maxDepth The maximum nesting depth.
	 * @return The string representation of the value of this Tag.
	 * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
	 * */
	public abstract String valueToString(int maxDepth);

	/**
	 * Returns whether this Tag and some other Tag are equal.
	 * They are equal if {@code other} is not {@code null} and they are of the same class.
	 * Custom Tag implementations should overwrite this but check the result
	 * of this {@code super}-method while comparing.
	 * @param other The Tag to compare to.
	 * @return {@code true} if they are equal based on the conditions mentioned above.
	 * */
	@Override
	public boolean equals(Object other) {
		return other != null && getClass() == other.getClass();
	}

	/**
	 * Calculates the hash code of this Tag. Tags which are equal according to {@link Tag#equals(Object)}
	 * must return an equal hash code.
	 * @return The hash code of this Tag.
	 * */
	@Override
	public int hashCode() {
		return value.hashCode();
	}

	/**
	 * Creates a clone of this Tag.
	 * @return A clone of this Tag.
	 * */
	public abstract Tag<T> clone();

	/**
	 * Escapes a string to fit into a JSON-like string representation for Minecraft
	 * or to create the JSON string representation of a Tag returned from {@link Tag#toString()}
	 * @param s The string to be escaped.
	 * @param lenient {@code true} if it should force double quotes ({@code "}) at the start and
	 *                the end of the string.
	 * @return The escaped string.
	 * */
	protected static String escapeString(String s, boolean lenient) {
		StringBuffer sb = new StringBuffer();
		Matcher m = ESCAPE_PATTERN.matcher(s);
		while (m.find()) {
			m.appendReplacement(sb, ESCAPE_CHARACTERS.get(m.group()));
		}
		m.appendTail(sb);
		m = NON_QUOTE_PATTERN.matcher(s);
		if (!lenient || !m.matches()) {
			sb.insert(0, "\"").append("\"");
		}
		return sb.toString();
	}
}

```

## main\java\cn\myfrank\stationbuilder\mixin\mtr\EnumPSDAPGItemAccessor.java

```java
package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemPSDAPGBase.EnumPSDAPGItem.class)
public interface EnumPSDAPGItemAccessor {
    @Accessor("isDoor")
    boolean isDoor();
}
```

## main\java\cn\myfrank\stationbuilder\mixin\mtr\EnumPSDAPGTypeAccessor.java

```java
package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemPSDAPGBase.EnumPSDAPGType.class)
public interface EnumPSDAPGTypeAccessor {
    @Accessor("isPSD")
    boolean isPSD();

    @Accessor("isOdd")
    boolean isOdd();

    @Accessor("isLift")
    boolean isLift();
}
```

## main\java\cn\myfrank\stationbuilder\mixin\mtr\ItemPSDAPGBaseAccessor.java

```java
package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mapping.holder.BlockState;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemPSDAPGBase.class)
public interface ItemPSDAPGBaseAccessor {
	@Accessor("item")
	ItemPSDAPGBase.EnumPSDAPGItem item_();

	@Accessor("type")
	ItemPSDAPGBase.EnumPSDAPGType type_();

	@Invoker("getBlockStateFromItem")
	BlockState getBlockStateFromItem_();
}
```

## main\java\cn\myfrank\stationbuilder\mixin\mtr\ItemRailModifierAccessor.java

```java
package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRailModifier.class)
public interface ItemRailModifierAccessor {
    @Accessor("railType")
    RailType railType_();
}

```

## client\java\cn\myfrank\stationbuilder\BuildingPlacerScreen.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;

import java.io.File;

public class BuildingPlacerScreen extends GuiScreen {
    private final GuiLabelTextField presetField = new GuiLabelTextField(Text.translatable("gui.stationbuilder.building_placer_template"), 120, 18, Text.empty());
    private BlockRotation rotation = BlockRotation.NONE;
    private boolean placeAir = false;

    private final GuiButton rotBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(BlockRotation.NONE)), b -> {
        BlockRotation[] rots = BlockRotation.values();
        rotation = rots[(rotation.ordinal() + 1) % rots.length];
        b.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
    }, 150, 18);

    private final GuiButton airBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_air_off"), b -> {
        placeAir = !placeAir;
        b.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }, 150, 18);

    public BuildingPlacerScreen(NbtCompound nbt) {
        super(Text.translatable("gui.stationbuilder.building_placer_tool"));
        if (nbt.contains("presetName")) presetField.setText(nbt.getString("presetName"));
        if (nbt.contains("rotation")) rotation = BlockRotation.valueOf(nbt.getString("rotation"));
        if (nbt.contains("placeAir")) placeAir = nbt.getBoolean("placeAir");
        rotBtn.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
        airBtn.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(5);
        rootPanel.setPadding(20);

        rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_placer_tool").formatted(net.minecraft.util.Formatting.GOLD)));

        // 新增：构建水平面板使输入、浏览、导入按钮紧凑地在同一行排列
        int widthVal = this.width - (rootPanel.getMarginLeft() + rootPanel.getPaddingLeft() + rootPanel.getMarginRight() + rootPanel.getPaddingRight());
        GuiPanel templatePanel = new GuiPanel(widthVal, 18);
        templatePanel.setDirection(GuiPanel.PanelDirection.HORIZONTAL);
        templatePanel.setGap(5);
        templatePanel.setMajorAlign(GuiPanel.MajorAlignMode.START);
        templatePanel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);

        GuiButton browseButton = new GuiButton(
                Text.translatable("gui.stationbuilder.browse"),
                b -> {
                    if (client != null) {
                        client.setScreen(new BuildingSelectionScreen(this, presetField.getTextField()));
                    }
                },
                50, 18
        );

        GuiButton importButton = new GuiButton(
                Text.translatable("gui.stationbuilder.import_file"),
                b -> openFileChooser(),
                70, 18
        );

        templatePanel.addControl(presetField);
        templatePanel.addControl(browseButton);
        templatePanel.addControl(importButton);

        rootPanel.addControl(templatePanel);
        rootPanel.addControl(rotBtn);
        rootPanel.addControl(airBtn);

        rootPanel.addControl(new GuiButton(Text.translatable("gui.stationbuilder.building_placer_save"), b -> {
            this.close();
        }, 100, 18));
    }

    // 新增：选取本地外部结构文件
    public void openFileChooser() {
        String path;
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.flip();

            path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    Text.translatable("control.stationbuilder.select_struct_file").getString(),
                    null,
                    filters,
                    Text.translatable("control.stationbuilder.struct_file_filter").getString(),
                    false
            );
        }

        if (path != null) {
            java.io.File file = new java.io.File(path);
            java.util.concurrent.CompletableFuture.supplyAsync(() -> loadStructureFromFile(file))
                    .thenAcceptAsync(template -> {
                        if (template != null) {
                            String name = file.getName().replaceFirst("\\.[^.]+$", "");
                            BuildingTemplateManager.addTemplate(name, template);
                            presetField.setText(name);

                            if (client != null && client.player != null) {
                                client.player.sendMessage(Text.translatable("gui.stationbuilder.import_success", name), false);
                            }
                        } else {
                            if (client != null && client.player != null) {
                                client.player.sendMessage(Text.translatable("gui.stationbuilder.import_fail"), true);
                            }
                        }
                    }, client::execute);
        }
    }

    // 新增：底层解析不同的外部结构格式
    private StructureTemplate loadStructureFromFile(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".nbt")) {
                NbtCompound nbt = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                StructureTemplate template = new StructureTemplate();
                template.readNbt(Registries.BLOCK, nbt);
                return template;
            } else if (name.endsWith(".schem") || name.endsWith(".schematic")) {
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else if (name.endsWith(".litematic")) {
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else {
                return null;
            }
        } catch (Exception e) {
            StationBuilder.LOGGER.error("Failed to load structure file: {}", file, e);
            return null;
        }
    }

    @Override
    public void close() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("presetName", presetField.getText().trim());
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);
        ClientPlayNetworking.send(new StationBuilder.SavePlacerPayload(nbt));
        super.close();
    }
}
```

## client\java\cn\myfrank\stationbuilder\BuildingSelectionScreen.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.gui.GuiTextField;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

@Environment(EnvType.CLIENT)
public class BuildingSelectionScreen extends Screen {
    private final Screen parent;
    private final GuiTextField buildingPresetField;
    private List<String> buildingNames;

    public BuildingSelectionScreen(Screen parent, GuiTextField buildingPresetField) {
        super(Text.translatable("gui.stationbuilder.select_building"));
        this.parent = parent;
        this.buildingPresetField = buildingPresetField;
        this.buildingNames = BuildingTemplateManager.getTemplateNames();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;

        // 显示模板列表按钮
        for (int i = 0; i < buildingNames.size(); i++) {
            String name = buildingNames.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
                buildingPresetField.setText(name);
                // 同步更新元素
                if (parent instanceof StationEditorScreen editor) {
                    int index = editor.getSelectedIndex();
                    if (index >= 0 && editor.getElements().get(index) instanceof BuildingElement be) {
                        be.presetName = name;
                    }
                }
                client.setScreen(parent);
            }).dimensions(centerX - 100, 40 + i * 25, 200, 20).build());
        }

        // 导入文件按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.import_file"), b -> {
            if (parent instanceof StationEditorScreen editor) {
                editor.openFileChooser();
            } else if (parent instanceof BuildingPlacerScreen placer) {
                placer.openFileChooser();
            }
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 60, 100, 20).build());

        // 取消按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
```

## client\java\cn\myfrank\stationbuilder\BuildingSelectorScreen.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.GuiButton;
import cn.myfrank.stationbuilder.gui.GuiLabel;
import cn.myfrank.stationbuilder.gui.GuiLabelTextField;
import cn.myfrank.stationbuilder.gui.GuiScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BuildingSelectorScreen extends GuiScreen {
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final GuiLabelTextField nameField = new GuiLabelTextField(Text.translatable("gui.stationbuilder.building_selector_template"), 150, 18, Text.literal("my_building"));
    private boolean includeEntities = false;
    private final GuiButton entityToggleBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_selector_include_entities_off"), b -> {
        includeEntities = !includeEntities;
        if (includeEntities) {
            b.setMessage(Text.translatable("gui.stationbuilder.building_selector_include_entities_on"));
        } else {
            b.setMessage(Text.translatable("gui.stationbuilder.building_selector_include_entities_off"));
        }
    }, 150, 18);

    public BuildingSelectorScreen(BlockPos pos1, BlockPos pos2) {
        super(Text.literal("Selection Tool"));
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(4);
        rootPanel.setPadding(15);

        rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_config").formatted(net.minecraft.util.Formatting.GOLD)));

        if (pos1 == null) {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos1", "N/A").formatted(net.minecraft.util.Formatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos1", pos1.toShortString())));
        }

        if (pos2 == null) {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos2", "N/A").formatted(net.minecraft.util.Formatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos2", pos2.toShortString())));
        }

        rootPanel.addControl(nameField);
        rootPanel.addControl(entityToggleBtn);

        rootPanel.addControl(new GuiButton(Text.translatable("gui.stationbuilder.building_selector_save"), b -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty() && pos1 != null && pos2 != null) {
                ClientPlayNetworking.send(new StationBuilder.SaveSelectionPayload(name, pos1, pos2, includeEntities));
                this.close();
            }
        }, 180, 18));
    }
}
```

## client\java\cn\myfrank\stationbuilder\PresetSaveScreen.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PresetSaveScreen extends Screen {
    private final StationEditorScreen parent;
    private TextFieldWidget nameField;

    public PresetSaveScreen(StationEditorScreen parent) {
        super(Text.translatable("gui.stationbuilder.save_preset"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client == null) return;
        int centerX = width / 2;
        int centerY = height / 2;

        this.nameField = new TextFieldWidget(textRenderer, centerX - 80, centerY - 10, 160, 20, Text.empty());
        this.addSelectableChild(nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.confirm"), b -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                PresetManager.savePreset(name, parent.getStationLength(), parent.getElements());
                client.setScreen(parent);
            }
        }).dimensions(centerX - 82, centerY + 20, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX + 2, centerY + 20, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, height / 2 - 30, 0xFFFFFF);
        this.nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}
```

## client\java\cn\myfrank\stationbuilder\PresetSelectionScreen.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.util.List;

public class PresetSelectionScreen extends Screen {
    private final StationEditorScreen parent;

    public PresetSelectionScreen(StationEditorScreen parent) {
        super(Text.translatable("gui.stationbuilder.presets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client == null) return;
        List<String> presets = PresetManager.getPresetList();
        int centerX = width / 2;

        // 渲染预设列表按钮
        for (int i = 0; i < presets.size(); i++) {
            String name = presets.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
                parent.applyPreset(PresetManager.loadPreset(name));
                client.setScreen(parent);
            }).dimensions(centerX - 100, 40 + (i * 25), 200, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 30, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.open_folder"), b -> {
            File dir = PresetManager.getPresetPath().toFile();
            // 如果文件夹不存在则创建，否则打开会失败
            if (!dir.exists()) dir.mkdirs();
            // 使用原版 Util 方法安全地打开系统文件夹
            Util.getOperatingSystem().open(dir);
        }).dimensions(width - 110, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
```

## client\java\cn\myfrank\stationbuilder\RailBuilderScreen.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.*;
import cn.myfrank.stationbuilder.gui.GuiPanel.CrossAlignMode;
import cn.myfrank.stationbuilder.gui.GuiPanel.PanelDirection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RailBuilderScreen extends GuiScreen {
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_WIDTH_S = 60;
    private static final int INPUT_WIDTH_S = 30;
    private static final int INPUT_HEIGHT = 18;
    private int catenaryState = 1; // 0=Disabled, 1=Vanilla, 2=MSD

    private String initialRailCount;
    private final GuiLabelTextField railCountInput = new GuiLabelTextField(getText("rail_count"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("2"));
    private final GuiLabelTextField railSpacingInput = new GuiLabelTextField(getText("rail_spacing"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("5.0"));
    private final GuiLabelSlot ballastBlockInput = new GuiLabelSlot(getText("ballast_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiLabelSlot railTypeInput = new GuiLabelSlot(getText("rail_type"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "rail"), false);
    private final GuiLabelTextField ballastTopWidthInput = new GuiLabelTextField(getText("ballast_top_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("5.0"));
    private final GuiLabelTextField ballastBottomWidthInput = new GuiLabelTextField(getText("ballast_bottom_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("11.0"));
    private final GuiLabelTextField ballastMaxThicknessInput = new GuiLabelTextField(getText("ballast_max_thickness"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("4"));
    private final GuiLabelTextField bridgeClearSpanInput = new GuiLabelTextField(getText("bridge_clear_span"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("50"));
    private final GuiLabelSlot bridgeGuardRailBlockInput = new GuiLabelSlot(getText("bridge_guardrail_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelSlot bridgeBlockInput = new GuiLabelSlot(getText("bridge_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "smooth_stone"), false);
    private final GuiLabelSlot bridgePillarBlockInput = new GuiLabelSlot(getText("bridge_pillar_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelTextField bridgeWidthInput = new GuiLabelTextField(getText("bridge_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7.0"));
    private final GuiLabelTextField tunnelHeightInput = new GuiLabelTextField(getText("tunnel_height"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7"));
    private final GuiLabelSlot tunnelWallBlockInput = new GuiLabelSlot(getText("tunnel_wall_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone"), false);
    private final GuiLabelSlot tunnelCeilingBlockInput = new GuiLabelSlot(getText("tunnel_ceiling_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelSlot tunnelFloorBlockInput = new GuiLabelSlot(getText("tunnel_floor_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiLabelTextField tunnelWidthInput = new GuiLabelTextField(getText("tunnel_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7.0"));
    private boolean clearFullHeight = false;
    private final GuiLabelButton clearModeButton = new GuiLabelButton(getText("clear_mode_v"), (button) -> {
        clearFullHeight = !clearFullHeight;
        syncClearMode();
    }, BUTTON_WIDTH_S, INPUT_HEIGHT, getText("clear_mode"));

    private final GuiButton catenaryStateButton = new GuiButton(getText("catenary_vanilla"), (button) -> {
        catenaryState = (catenaryState + 1) % (StationBuilder.isMsdLoaded() ? 3 : 2);
        syncCatenaryState();
    }, BUTTON_WIDTH, INPUT_HEIGHT);
    private final GuiLabelSlot catenaryLineBlockInput = new GuiLabelSlot(getText("catenary_line_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "cobweb"), false);
    private final GuiLabelTextField catenarySpacingInput = new GuiLabelTextField(getText("catenary_spacing"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("50"));
    private int catenaryModeIndex = 0;
    private final GuiButton catenaryModeButton = new GuiButton(getText("catenary"), (button) -> {
        catenaryModeIndex = (catenaryModeIndex + 1) % CatenaryTypeMapping.values().length;
        syncCatenaryMode(button);
        syncCatenaryState();
    }, BUTTON_WIDTH, INPUT_HEIGHT);
    private final GuiLabelSlot catenaryBridgePillarInput = new GuiLabelSlot(getText("catenary_bridge_pillar"), INPUT_WIDTH_S, INPUT_HEIGHT, StationBuilder.isMsdLoaded() ? new Identifier("msd", "catenary_with_long") : new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelSlot catenaryTunnelPillarInput = new GuiLabelSlot(getText("catenary_tunnel_pillar"), INPUT_WIDTH_S, INPUT_HEIGHT, StationBuilder.isMsdLoaded() ? new Identifier("msd", "catenary_with_long_top") : new Identifier("minecraft", "stone_brick_wall"), false);
    private final GhostInventory ghostInventory = new GhostInventory();

    private void syncCatenaryMode(GuiButton button) {
        var type = CatenaryTypeMapping.values()[catenaryModeIndex];
        button.setMessage(Text.translatable("gui.stationbuilder." + type.getName()));
    }
    private void syncClearMode() {
        clearModeButton.setMessage(getText(clearFullHeight ? "clear_mode_v" : "clear_mode_tunnel"));
    }
    private void syncCatenaryState() {
        if (catenaryState == 0) {
            catenaryStateButton.setMessage(getText("catenary_disabled"));
            catenarySpacingInput.setVisible(false);
            catenaryModeButton.setVisible(false);
            catenaryLineBlockInput.setVisible(false);
            catenaryBridgePillarInput.setVisible(false);
            catenaryTunnelPillarInput.setVisible(false);
        } else if (catenaryState == 1) {
            catenaryStateButton.setMessage(getText("catenary_vanilla"));
            catenarySpacingInput.setVisible(true);
            catenaryModeButton.setVisible(false);
            catenaryLineBlockInput.setVisible(true);
            catenaryBridgePillarInput.setVisible(true);
            catenaryTunnelPillarInput.setVisible(true);
        } else {
            catenaryStateButton.setMessage(getText("catenary_msd"));
            catenarySpacingInput.setVisible(true);
            catenaryModeButton.setVisible(true);
            catenaryLineBlockInput.setVisible(false);
            catenaryBridgePillarInput.setVisible(true);
            catenaryTunnelPillarInput.setVisible(true);
            boolean isAuto = catenaryModeIndex == 0;
            catenaryBridgePillarInput.setVisible(!isAuto);
            catenaryTunnelPillarInput.setVisible(!isAuto);
        }
    }
    private static Text getText(String key) {
        return Text.translatable("gui.stationbuilder." + key);
    }

    public RailBuilderScreen(NbtCompound nbt) {
        super(getText("rail_builder"));
        this.railCountInput.getTextField().setNumberOnly(true);
        if (nbt.contains("railCount", NbtElement.INT_TYPE)) {
            this.railCountInput.setText(String.valueOf(nbt.getInt("railCount")));
        }
        if (nbt.contains("railSpacing", NbtElement.DOUBLE_TYPE)) {
            this.railSpacingInput.setText(String.valueOf(nbt.getDouble("railSpacing")));
        }
        if (nbt.contains("ballastBlock", NbtElement.STRING_TYPE)) {
            this.ballastBlockInput.setBlockId(new Identifier(nbt.getString("ballastBlock")));
        }
        if (nbt.contains("railType", NbtElement.STRING_TYPE)) {
            this.railTypeInput.setBlockId(new Identifier(nbt.getString("railType")));
        }
        this.ballastTopWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastTopWidth", NbtElement.DOUBLE_TYPE)) {
            this.ballastTopWidthInput.setText(String.valueOf(nbt.getDouble("ballastTopWidth")));
        }
        this.ballastBottomWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastBottomWidth", NbtElement.DOUBLE_TYPE)) {
            this.ballastBottomWidthInput.setText(String.valueOf(nbt.getDouble("ballastBottomWidth")));
        }
        this.ballastMaxThicknessInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastMaxThickness", NbtElement.INT_TYPE)) {
            this.ballastMaxThicknessInput.setText(String.valueOf(nbt.getInt("ballastMaxThickness")));
        }
        this.bridgeClearSpanInput.getTextField().setNumberOnly(true);
        if (nbt.contains("bridgeClearSpan", NbtElement.INT_TYPE)) {
            this.bridgeClearSpanInput.setText(String.valueOf(nbt.getInt("bridgeClearSpan")));
        }
        if (nbt.contains("bridgeGuardRailBlock", NbtElement.STRING_TYPE)) {
            this.bridgeGuardRailBlockInput.setBlockId(new Identifier(nbt.getString("bridgeGuardRailBlock")));
        }
        if (nbt.contains("bridgeBlock", NbtElement.STRING_TYPE)) {
            this.bridgeBlockInput.setBlockId(new Identifier(nbt.getString("bridgeBlock")));
        }
        if (nbt.contains("bridgePillarBlock", NbtElement.STRING_TYPE)) {
            this.bridgePillarBlockInput.setBlockId(new Identifier(nbt.getString("bridgePillarBlock")));
        }
        this.bridgeWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("bridgeWidth", NbtElement.DOUBLE_TYPE)) {
            this.bridgeWidthInput.setText(String.valueOf(nbt.getDouble("bridgeWidth")));
        }
        this.tunnelHeightInput.getTextField().setNumberOnly(true);
        if (nbt.contains("tunnelHeight", NbtElement.INT_TYPE)) {
            this.tunnelHeightInput.setText(String.valueOf(nbt.getInt("tunnelHeight")));
        }
        if (nbt.contains("tunnelWallBlock", NbtElement.STRING_TYPE)) {
            this.tunnelWallBlockInput.setBlockId(new Identifier(nbt.getString("tunnelWallBlock")));
        }
        if (nbt.contains("tunnelCeilingBlock", NbtElement.STRING_TYPE)) {
            this.tunnelCeilingBlockInput.setBlockId(new Identifier(nbt.getString("tunnelCeilingBlock")));
        }
        if (nbt.contains("tunnelFloorBlock", NbtElement.STRING_TYPE)) {
            this.tunnelFloorBlockInput.setBlockId(new Identifier(nbt.getString("tunnelFloorBlock")));
        }
        this.tunnelWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("tunnelWidth", NbtElement.DOUBLE_TYPE)) {
            this.tunnelWidthInput.setText(String.valueOf(nbt.getDouble("tunnelWidth")));
        }
        if (nbt.contains("clearFullHeight")) {
            this.clearFullHeight = nbt.getBoolean("clearFullHeight");
        } else {
            this.clearFullHeight = true;
        }
        syncClearMode();

        boolean useCat = !nbt.contains("useCatenary") || nbt.getBoolean("useCatenary");
        boolean isVan = nbt.contains("isVanillaCatenary") ? nbt.getBoolean("isVanillaCatenary") : !StationBuilder.isMsdLoaded();
        if (!useCat) {
            this.catenaryState = 0;
        } else if (isVan) {
            this.catenaryState = 1;
        } else {
            this.catenaryState = StationBuilder.isMsdLoaded() ? 2 : 1;
        }

        if (nbt.contains("catenaryModeIndex", NbtElement.INT_TYPE)) {
            this.catenaryModeIndex = nbt.getInt("catenaryModeIndex");
        } else {
            this.catenaryModeIndex = 0;
        }
        syncCatenaryMode(catenaryModeButton);
        syncCatenaryState();

        if (nbt.contains("catenaryBlock", NbtElement.STRING_TYPE)) {
            this.catenaryLineBlockInput.setBlockId(new Identifier(nbt.getString("catenaryBlock")));
        }
        this.catenarySpacingInput.getTextField().setNumberOnly(true);
        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE)) {
            this.catenarySpacingInput.setText(String.valueOf(nbt.getInt("catenarySpacing")));
        }
        if (nbt.contains("catenaryBridgePillar", NbtElement.STRING_TYPE)) {
            this.catenaryBridgePillarInput.setBlockId(new Identifier(nbt.getString("catenaryBridgePillar")));
        }
        if (nbt.contains("catenaryTunnelPillar", NbtElement.STRING_TYPE)) {
            this.catenaryTunnelPillarInput.setBlockId(new Identifier(nbt.getString("catenaryTunnelPillar")));
        }
        this.initialRailCount = nbt.contains("railCount", NbtElement.INT_TYPE)
                ? String.valueOf(nbt.getInt("railCount"))
                : "2"; // 默认值应与 railCountInput 的默认文本一致
    }

    public NbtCompound getNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("railCount", Integer.parseInt(railCountInput.getText()));
        nbt.putDouble("railSpacing", Double.parseDouble(railSpacingInput.getText()));
        nbt.putString("ballastBlock", ballastBlockInput.getBlockId().toString());
        nbt.putString("railType", railTypeInput.getBlockId().toString());
        nbt.putDouble("ballastTopWidth", Double.parseDouble(ballastTopWidthInput.getText()));
        nbt.putDouble("ballastBottomWidth", Double.parseDouble(ballastBottomWidthInput.getText()));
        nbt.putInt("ballastMaxThickness", Integer.parseInt(ballastMaxThicknessInput.getText()));
        nbt.putInt("bridgeClearSpan", Integer.parseInt(bridgeClearSpanInput.getText()));
        nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlockInput.getBlockId().toString());
        nbt.putString("bridgeBlock", bridgeBlockInput.getBlockId().toString());
        nbt.putString("bridgePillarBlock", bridgePillarBlockInput.getBlockId().toString());
        nbt.putDouble("bridgeWidth", Double.parseDouble(bridgeWidthInput.getText()));
        nbt.putInt("tunnelHeight", Integer.parseInt(tunnelHeightInput.getText()));
        nbt.putString("tunnelWallBlock", tunnelWallBlockInput.getBlockId().toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlockInput.getBlockId().toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlockInput.getBlockId().toString());
        nbt.putDouble("tunnelWidth", Double.parseDouble(tunnelWidthInput.getText()));
        nbt.putBoolean("clearFullHeight", clearFullHeight);
        nbt.putBoolean("useCatenary", catenaryState != 0);
        nbt.putBoolean("isVanillaCatenary", catenaryState == 1);
        nbt.putString("catenaryBlock", catenaryLineBlockInput.getBlockId().toString());
        nbt.putInt("catenarySpacing", Integer.parseInt(catenarySpacingInput.getText()));
        nbt.putInt("catenaryModeIndex", catenaryModeIndex);
        nbt.putString("catenaryBridgePillar", catenaryBridgePillarInput.getBlockId().toString());
        nbt.putString("catenaryTunnelPillar", catenaryTunnelPillarInput.getBlockId().toString());
        return nbt;
    }
    
    @Override
    protected void initControls() {
        int w = (width - 15) / 4, h = 110;
        var topPanel = new GuiPanel(width - 15, h);
        topPanel.setGap(2);
        topPanel.setDirection(PanelDirection.HORIZONTAL);

        var bottomPanel = new GuiPanel(width - 15, h);
        bottomPanel.setGap(2);
        bottomPanel.setDirection(PanelDirection.HORIZONTAL);

        var railPanel = new GuiPanel(w, h);
        railPanel
                .addControl(new GuiLabel(getText("rail")))
                .addControl(railCountInput)
                .addControl(railSpacingInput)
                .addControl(railTypeInput)
                .addControl(clearModeButton)
                .setDirection(PanelDirection.VERTICAL);
        railPanel.setCrossAlign(CrossAlignMode.START);
        railPanel.setGap(2);

        var ballastPanel = new GuiPanel(w, h);
        ballastPanel.addControl(new GuiLabel(getText("ballast")))
            .addControl(ballastBlockInput)
            .addControl(ballastTopWidthInput)
            .addControl(ballastBottomWidthInput)
            .addControl(ballastMaxThicknessInput)
            .setDirection(PanelDirection.VERTICAL);
        ballastPanel.setCrossAlign(CrossAlignMode.START);
        ballastPanel.setGap(2);

        var bridgePanel = new GuiPanel(w + 5, h);
        bridgePanel
            .addControl(new GuiLabel(getText("bridge")))
            .addControl(bridgeClearSpanInput)
            .addControl(bridgeGuardRailBlockInput)
            .addControl(bridgeBlockInput)
            .addControl(bridgePillarBlockInput)
            .addControl(bridgeWidthInput)
            .setDirection(PanelDirection.VERTICAL);
        bridgePanel.setCrossAlign(CrossAlignMode.START);
        bridgePanel.setGap(2);

        var tunnelPanel = new GuiPanel(w, h);
        tunnelPanel
            .addControl(new GuiLabel(getText("tunnel")))
            .addControl(tunnelHeightInput)
            .addControl(tunnelWallBlockInput)
            .addControl(tunnelCeilingBlockInput)
            .addControl(tunnelFloorBlockInput)
            .addControl(tunnelWidthInput)
            .setDirection(PanelDirection.VERTICAL);
        tunnelPanel.setCrossAlign(CrossAlignMode.START);
        tunnelPanel.setGap(2);

        var catenaryPanel = new GuiPanel(w * 2, h);
        catenaryPanel
                .addControl(new GuiLabel(getText("catenary")))
                .addControl(catenaryStateButton)
                .addControl(catenarySpacingInput)
                .addControl(catenaryModeButton)
                .addControl(catenaryLineBlockInput)
                .addControl(catenaryBridgePillarInput)
                .addControl(catenaryTunnelPillarInput)
                .setDirection(PanelDirection.VERTICAL);
        catenaryPanel.setCrossAlign(CrossAlignMode.START);
        catenaryPanel.setGap(2);

        topPanel.addControl(railPanel).addControl(ballastPanel).addControl(catenaryPanel);
        addControl(topPanel);

        bottomPanel.addControl(bridgePanel).addControl(tunnelPanel).addControl(ghostInventory);
        addControl(bottomPanel);
    }

    @Override
    public void close() {
        String currentRailCount = railCountInput.getText();
        // 如果 railCount 发生了变化，先清除状态
        if (!currentRailCount.equals(initialRailCount)) {
            ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.create());
        }
        // 关闭时自动发送保存包
        sendSyncPacket(StationBuilder.SAVE_DATA_PACKET_RAIL);
        super.close();
    }

    private void sendSyncPacket(Identifier packetId) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeNbt(getNbt());
            ClientPlayNetworking.send(packetId, buf);
        } catch (Exception ignored) {}
    }
}

```

## client\java\cn\myfrank\stationbuilder\RailPreviewCache.java

```java
package cn.myfrank.stationbuilder;

import net.minecraft.util.math.BlockPos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RailPreviewCache {
    // 内部记录类
    // 键类，用于作为HashMap的键
    private record CacheKey(BlockPos start, float startAngle, BlockPos end, float endAngle) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return Float.compare(cacheKey.startAngle, startAngle) == 0 &&
                    Float.compare(cacheKey.endAngle, endAngle) == 0 &&
                    Objects.equals(start, cacheKey.start) &&
                    Objects.equals(end, cacheKey.end);
        }

    }

    // FIFO缓存实现 - 使用插入顺序，而不是访问顺序
    private final Map<CacheKey, TestConnectResult> cache;

    // 默认最大条目数
    private static final int DEFAULT_MAX_ENTRIES = 100;
    private final int maxEntries;

    /**
     * 使用默认配置创建缓存（最大100100条）
     */
    public RailPreviewCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * 使用自定义配置创建缓存
     * @param maxEntries 最大条目数
     */
    public RailPreviewCache(int maxEntries) {
        this.maxEntries = maxEntries;

        // 创建LinkedHashMap，设置accessOrder为false以保持插入顺序
        // 当超过最大条目数时自动移除最先插入的条目
        this.cache = new LinkedHashMap<CacheKey, TestConnectResult>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, TestConnectResult> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * 插入缓存记录
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @param result 测试连接结果
     */
    public void put(BlockPos start, float startAngle, BlockPos end, float endAngle, final TestConnectResult result) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        cache.put(key, result);
    }

    /**
     * 查询并获取测试连接结果
     * 注意：访问不会改变条目的顺序
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @return 测试连接结果，如果不存在则返回null
     */
    public TestConnectResult get(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        return cache.get(key);
    }

    /**
     * 检查是否存在缓存
     * 注意：访问不会改变条目的顺序
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     * @return 是否存在缓存
     */
    public boolean contains(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        return cache.containsKey(key);
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 移除指定缓存
     * @param start 起点位置
     * @param startAngle 起点角度
     * @param end 终点位置
     * @param endAngle 终点角度
     */
    public void remove(BlockPos start, float startAngle, BlockPos end, float endAngle) {
        CacheKey key = new CacheKey(start, startAngle, end, endAngle);
        cache.remove(key);
    }

    /**
     * 获取缓存大小
     * @return 缓存中的记录数量
     */
    public int size() {
        return cache.size();
    }

    /**
     * 检查缓存是否为空
     * @return 缓存是否为空
     */
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * 获取最大条目数
     * @return 最大条目数
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 手动触发清理（移除最早插入的条目直到达到指定数量）
     */
    public void trimToSize(int size) {
        if (size < 0 || size >= cache.size()) {
            return;
        }

        // 移除最早插入的条目（LinkedHashMap的第一个条目）
        while (cache.size() > size) {
            // 获取第一个（最早插入的）条目
            CacheKey firstKey = cache.keySet().iterator().next();
            cache.remove(firstKey);
        }
    }

    /**
     * 检查缓存是否已满
     * @return 是否已满
     */
    public boolean isFull() {
        return cache.size() >= maxEntries;
    }

    /**
     * 获取最早插入的条目
     * @return 最早插入的TestConnectResult，如果缓存为空则返回null
     */
    public TestConnectResult getOldest() {
        if (cache.isEmpty()) {
            return null;
        }

        // LinkedHashMap的第一个条目是最早插入的
        return cache.values().iterator().next();
    }

    /**
     * 获取最晚插入的条目
     * @return 最晚插入的TestConnectResult，如果缓存为空则返回null
     */
    public TestConnectResult getNewest() {
        if (cache.isEmpty()) {
            return null;
        }

        // 遍历到最后一个条目
        java.util.Iterator<TestConnectResult> iterator = cache.values().iterator();
        TestConnectResult last = null;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    /**
     * 移除最早插入的条目
     * @return 被移除的条目，如果缓存为空则返回null
     */
    public TestConnectResult removeOldest() {
        if (cache.isEmpty()) {
            return null;
        }

        // 获取第一个（最早插入的）键
        CacheKey firstKey = cache.keySet().iterator().next();
        return cache.remove(firstKey);
    }

    /**
     * 获取缓存统计信息
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("缓存统计: 当前条目数=%d, 最大条目数=%d, 使用率=%.1f%%",
                size(), maxEntries, (size() * 100.0 / maxEntries));
    }

    /**
     * 获取缓存中的所有键（按插入顺序）
     * @return 按插入顺序排列的键列表
     */
    public java.util.List<CacheKey> getKeysInOrder() {
        return new java.util.ArrayList<>(cache.keySet());
    }

    /**
     * 获取缓存中的所有值（按插入顺序）
     * @return 按插入顺序排列的值列表
     */
    public java.util.List<TestConnectResult> getValuesInOrder() {
        return new java.util.ArrayList<>(cache.values());
    }
}
```

## client\java\cn\myfrank\stationbuilder\StationBuilderClient.java

```java
package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

public class StationBuilderClient implements ClientModInitializer {
	private static final RailPreviewCache previewCache = new RailPreviewCache();
	@Override
	public void onInitializeClient() {
		StationBuilderKeyBindings.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (StationBuilderKeyBindings.CLEAR_RAIL_STATE.wasPressed() && client.player != null && client.player.getMainHandStack().getItem() instanceof RailBuilderItem) {
                ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.empty());
            }
        });

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET, (client, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			int facingIdx = buf.readInt();
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				client.setScreen(new StationEditorScreen(pos, Direction.fromHorizontal(facingIdx), nbt));
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(StationBuilder.SYNC_AND_OPEN_PACKET_RAIL, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				client.setScreen(new RailBuilderScreen(nbt));
			});
		});
		
		ModelPredicateProviderRegistry.register(
            ModItems.RAIL_BUILDER_ITEM,
            new Identifier("building"),
            (stack, world, entity, seed) -> {
                // 必须有 world（GUI / 手持时一定有）
                if (world == null) return 0;

                // 是否有 lastPos（建造中）
                if (!RailBuilderState.isBuilding(stack)) {
                    return 0;
                }

                // 用世界时间做闪烁（每 10 tick 切一次）
                long t = world.getTime();
                return (t / 10 % 2 == 0) ? 1 : 0;
            }
        );

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			ItemStack stack = client.player.getMainHandStack();
			if (!(stack.getItem() instanceof RailBuilderItem)) return;

			HitResult hit = client.crosshairTarget;
			if (!(hit instanceof BlockHitResult bhr)) return;

			var state = client.world.getBlockState(bhr.getBlockPos());
			var pos = bhr.getBlockPos();
			if (!StationBuilder.isSoftTransparent(state)) pos = pos.offset(bhr.getSide());

			renderRailPreview(context, client.player, pos, stack);
		});
	}

	private static void renderRailPreview(
			WorldRenderContext context,
			PlayerEntity player,
			BlockPos targetPos,
			ItemStack stack
	) {
		ArrayList<BlockPos> nodes = RailGenerator.calcRailNodes(
			targetPos, player.getYaw(), RailBuilderConfig.fromItem(stack)
		);
		if (nodes == null) return;
		VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLines());
		MatrixStack matrices = context.matrixStack();
		Vec3d cam = context.camera().getPos();

		var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
		if (lastPair == null || lastPair.left() == null) {
			for (BlockPos node : nodes) {
				drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
			}
		} else {
			var lastNodes = lastPair.left();
			float lastAngle = lastPair.right();

			if (lastNodes.size() != nodes.size()) {
				// 清除本地状态（物品NBT）
				RailBuilderState.clear(stack);
				stack.getOrCreateNbt().remove("CustomModelData");
				// 通知服务端清除状态
				ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.create());
				// 直接返回，不再绘制任何预览（避免越界）
				return;
			}

			RailMath.adjustPointSequence(lastNodes, nodes);
			float angle = player.getYaw();
			Vec3d textPos = getPreviewCenterPos(lastNodes, targetPos);
			var d = getDelta(lastNodes, targetPos);
			double minRadius = 1e9, minLength = 1e9;
			int successCount = 0;

			for (int i = 0; i < lastNodes.size(); ++i) {
				var node = nodes.get(i);
				var lastNode = lastNodes.get(i);
				drawBox(matrices, consumer, lastNode, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
				drawBox(matrices, consumer, node, cam, 0f, 1f, 1f, 0.6f); // 青色半透明
				if (StationBuilder.isMtrLoaded()) {
					var preview = previewCache.get(lastNode, MTRIntegration.parseAngle(lastAngle),
							node, MTRIntegration.parseAngle(angle));
					if(preview == null) {
						preview = MTRIntegration.testConnectRailNodes(lastAngle, angle, lastNode, node);
						previewCache.put(lastNode, MTRIntegration.parseAngle(lastAngle),
								node, MTRIntegration.parseAngle(angle), preview);
					}
					if (preview.success()) {
						successCount += 1;
						renderCurve(matrices, context.consumers(), context.camera(), preview.positions());
						if (preview.radius() > 0) {
							minRadius = Math.min(minRadius, preview.radius());
							minLength = Math.min(minLength, preview.length());
						}
					}
				}
			}
			String extras = "";
			int extra_color = 0xAAAAAA;
			if (successCount < lastNodes.size()) {
				extras = Text.translatable("message.stationbuilder.rail_builder.fail", lastNodes.size() - successCount).getString();
				if (successCount > 0) {
					extras += ", ";
				}
				extra_color = 0xFF5555;
			}
			if (successCount > 0) {
				if (minRadius < 1e9) {
					extras += Text.translatable("message.stationbuilder.rail_builder.min_radius", String.format("%.2f", minRadius), String.format("%.2f", minLength)).getString();
				} else {
					extras += Text.translatable("message.stationbuilder.rail_builder.straight_line").getString();
				}
			}
			renderDeltaText3D(context, textPos, d, extras, extra_color);
		}
	}

	private static void renderCurve(
			MatrixStack matrices,
			VertexConsumerProvider consumers,
			Camera camera,
			List<Vec3d> points
	) {
		if (points.size() < 2) return;

		Vec3d camPos = camera.getPos();

		VertexConsumer vc = consumers.getBuffer(RenderLayer.LINES);

		matrices.push();
		matrices.translate(-camPos.x, -camPos.y, -camPos.z);

		Matrix4f mat = matrices.peek().getPositionMatrix();

		for (int i = 0; i < points.size() - 1; i++) {
			Vec3d p0 = points.get(i);
			Vec3d p1 = points.get(i + 1);

			vc.vertex(mat, (float)p0.x, (float)p0.y, (float)p0.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0)
					.next();

			vc.vertex(mat, (float)p1.x, (float)p1.y, (float)p1.z)
					.color(255, 0, 0, 255)
					.normal(1, 0, 0)
					.next();
		}

		matrices.pop();
	}

	private static void drawBox(
			MatrixStack matrices,
			VertexConsumer consumer,
			BlockPos pos,
			Vec3d cam,
			float r, float g, float b, float a
	) {
		Box box = new Box(pos).offset(-cam.x, -cam.y, -cam.z);
		WorldRenderer.drawBox(matrices, consumer, box, r, g, b, a);
	}

	private static Vec3d getPreviewCenterPos(
			List<BlockPos> lastNodes,
			BlockPos anchor
	) {
		Vec3d lastCenter = Vec3d.ZERO;
		for (BlockPos p : lastNodes) {
			lastCenter = lastCenter.add(p.toCenterPos());
		}
		lastCenter = lastCenter.multiply(1.0 / lastNodes.size());

		Vec3d delta = anchor.toCenterPos().subtract(lastCenter);

		return lastCenter.add(delta).add(0, 0.5, 0); // 文本抬高一点
	}

	private static Vec3i getDelta(List<BlockPos> lastNodes, BlockPos anchor) {
		double sumX = 0, sumY = 0, sumZ = 0;
		for (BlockPos p : lastNodes) {
			sumX += p.getX();
			sumY += p.getY();
			sumZ += p.getZ();
		}
		int lastX = (int) Math.round(sumX / lastNodes.size());
		int lastY = (int) Math.round(sumY / lastNodes.size());
		int lastZ = (int) Math.round(sumZ / lastNodes.size());
		return new Vec3i(anchor.getX() - lastX,anchor.getY() - lastY, anchor.getZ() - lastZ);
	}

	private static void renderDeltaText3D(
			WorldRenderContext context,
			Vec3d worldPos,
			Vec3i d,
			String extras,
			int extra_color
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		Camera camera = context.camera();
		MatrixStack matrices = context.matrixStack();

		TextRenderer textRenderer = client.textRenderer;

		String text;
		int color;

		if (d.getY() > 0) {
			text = d.getX() + ", ↑ +" + d.getY() + ", " + d.getZ();
			color = 0x00FF00;
		} else if (d.getY() < 0) {
			text = d.getX() + ", ↓ " + d.getY() + ", " + d.getZ();
			color = 0xFF5555;
		} else {
			text = d.getX() + ", = 0, " + d.getZ();
			color = 0xAAAAAA;
		}

		matrices.push();

		// 世界坐标 → 相机坐标
		Vec3d camPos = camera.getPos();
		matrices.translate(
				worldPos.x - camPos.x,
				worldPos.y - camPos.y,
				worldPos.z - camPos.z
		);

		// billboard：始终面向玩家
		matrices.multiply(camera.getRotation());

		// 缩放（非常关键）
		float scale = 0.025F;
		matrices.scale(-scale, -scale, scale);

		// 文字居中
		float x = -textRenderer.getWidth(text) / 2f;
		float y = 0;

		RenderSystem.disableDepthTest();

		textRenderer.draw(
				text,
				x,
				y,
				color,
				false,
				matrices.peek().getPositionMatrix(),
				context.consumers(),
				TextRenderer.TextLayerType.NORMAL,
				0,
				0xF000F0
		);

		float x2 = -textRenderer.getWidth(extras) / 2f;
		textRenderer.draw(
				extras,
				x2,
				y + textRenderer.getWrappedLinesHeight(text, 100) + 3,
				extra_color,
				false,
				matrices.peek().getPositionMatrix(),
				context.consumers(),
				TextRenderer.TextLayerType.NORMAL,
				0,
				0xF000F0
		);

		RenderSystem.enableDepthTest();

		matrices.pop();
	}

}
```

## client\java\cn\myfrank\stationbuilder\StationBuilderKeyBindings.java

```java
package cn.myfrank.stationbuilder;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class StationBuilderKeyBindings {

    public static KeyBinding CLEAR_RAIL_STATE;

    public static void register() {
        CLEAR_RAIL_STATE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.stationbuilder.clear_rail_state",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_V,
                        "category.stationbuilder"
                )
        );
    }
}
```

## client\java\cn\myfrank\stationbuilder\StationEditorScreen.java

```java
package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.elements.PlatformElement;
import cn.myfrank.stationbuilder.elements.StationElement;
import cn.myfrank.stationbuilder.elements.TrackElement;
import cn.myfrank.stationbuilder.gui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;

import static cn.myfrank.stationbuilder.StationBuilder.SAVE_DATA_PACKET;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StationEditorScreen extends GuiScreen {
    private static final int BTN_WIDTH_XL = 75;
    private static final int BTN_WIDTH_L = 65;
    private static final int BTN_WIDTH = 50;
    private static final int BTN_WIDTH_S = 20;
    private static final int BTN_HEIGHT = 18;
    private static final int INPUT_WIDTH_L = 70;
    private static final int INPUT_WIDTH = 50;
    private static final int INPUT_WIDTH_S = 30;
    private static final int INPUT_HEIGHT = 18;

    private static Text getText(String key) {
        return Text.translatable("gui.stationbuilder." + key);
    }

    private String initialLength = String.valueOf(StationBuilder.DEFAULT_STATION_LENGTH);
    private final BlockPos pos;
    private final Direction facing;
    private final List<StationElement> elements = new ArrayList<>();

    private GuiPanel trackProperties;
    private GuiPanel platformProperties;
    private GuiPanel buildingProperties;
    private GuiPanel emptyProperties;
    private GuiRectCanvas canvas;
    private final GuiLabelTextField lengthField = new GuiLabelTextField(getText("length"), INPUT_WIDTH_S,
            INPUT_HEIGHT, Text.empty());
    private final GuiLabelSlot trackBallastField = new GuiLabelSlot(
            getText("ballast"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("minecraft", "andesite"), false
    );
    private final GuiLabel autoRotate = new GuiLabel(getText("auto_rotate"));
    private final GuiButton useMtrTrackButton = new GuiButton(getText("mtr_on"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof TrackElement t) {
            t.isMtrTrack = !t.isMtrTrack;
            if (t.isMtrTrack) {
                b.setMessage(getText("mtr_on"));
            }else{
                b.setMessage(getText("mtr_off"));
            }
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelTextField platformLengthField = new GuiLabelTextField(getText("width"), INPUT_WIDTH_S,
            INPUT_HEIGHT, Text.literal("9"));
    private final GuiLabelSlot platformSafetyField = new GuiLabelSlot(
            getText("safety_line"), INPUT_WIDTH_S, INPUT_HEIGHT,
            new Identifier("minecraft", "yellow_concrete"), false
    );
    private final GuiLabelSlotInput[] weightFields = new GuiLabelSlotInput[5];
    private final GuiLabelTextField buildingPresetField = new GuiLabelTextField(getText("preset"), INPUT_WIDTH_L,
            INPUT_HEIGHT, Text.empty());
    private final GhostInventory ghostInventory = new GhostInventory();
    private final GuiButton delButton = new GuiButton(getText("del"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && index < this.elements.size()) {
            elements.remove(index);
            canvas.removeSelectedRect();
            refreshPropertyArea();
        }
    }, BTN_WIDTH, BTN_HEIGHT);
    private final GuiButton moveLeftButton = new GuiButton(Text.literal("←"), b -> {
        int index = canvas.getSelectedIndex();
        if (index > 0 && index < this.elements.size()) {
            Collections.swap(elements, index, index - 1);
            canvas.swap(index, index - 1);
            refreshPropertyArea();
        }
    }, BTN_WIDTH_S, BTN_HEIGHT);
    private final GuiButton moveRightButton = new GuiButton(Text.literal("→"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && index < this.elements.size() - 1) {
            Collections.swap(elements, index, index + 1);
            canvas.swap(index, index + 1);
            refreshPropertyArea();
        }
    }, BTN_WIDTH_S, BTN_HEIGHT);
    private final GuiButton hasCanopyBtn = new GuiButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasCanopy = !pe.hasCanopy;
            b.setMessage(pe.hasCanopy ? getText("canopy_on") : getText("canopy_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelTextField canopyHeightField = new GuiLabelTextField(getText("canopy_height"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("4"));
    private final GuiLabelSlot canopySlabSlot = new GuiLabelSlot(getText("canopy_slab"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("minecraft", "stone_slab"), false);
    private final GuiLabelButton canopyStyleBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            PlatformElement.CanopyStyle[] styles = PlatformElement.CanopyStyle.values();
            pe.canopyStyle = styles[(pe.canopyStyle.ordinal() + 1) % styles.length];
            b.setMessage(getText(pe.canopyStyle.name()));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("canopy_style"));
    private final GuiLabelButton pillarStyleBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            // 循环切换枚举
            PlatformElement.PillarStyle[] styles = PlatformElement.PillarStyle.values();
            pe.pillarStyle = styles[(pe.pillarStyle.ordinal() + 1) % styles.length];
            b.setMessage(getText(pe.pillarStyle.name()));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("pillar_style"));
    private final GuiLabelSlot pillarBlockSlot = new GuiLabelSlot(getText("pillar_block"), INPUT_WIDTH,
            INPUT_HEIGHT, new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelTextField pillarSpacingField = new GuiLabelTextField(getText("pillar_spacing"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("6"));
    private final GuiLabelTextField pillarOffsetField = new GuiLabelTextField(getText("pillar_offset"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("2"));
    private final GuiButton hasLightingBtn = new GuiButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasLighting = !pe.hasLighting;
            b.setMessage(pe.hasLighting ? getText("lighting_on") : getText("lighting_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelSlot lightBlockSlot = new GuiLabelSlot(getText("lighting_block"), INPUT_WIDTH,
            INPUT_HEIGHT, new Identifier("minecraft", "sea_lantern"), false);
    private final GuiLabelButton shieldDoorBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasShieldDoors = !pe.hasShieldDoors;
            b.setMessage(pe.hasShieldDoors ? getText("psd_on") : getText("psd_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("psd"));;
    private final GuiLabelTextField doorOffsetField = new GuiLabelTextField(getText("door_offset"),
            INPUT_WIDTH, INPUT_HEIGHT, Text.literal("2"));
    private final GuiLabelTextField doorSpacingField = new GuiLabelTextField(getText("door_spacing"),
            INPUT_WIDTH, INPUT_HEIGHT, Text.literal("3"));;
    private final GuiLabelSlot psdEndSlot = new GuiLabelSlot(getText("psd_end"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "apg_glass_end"), false);
    private final GuiLabelSlot psdGlassSlot = new GuiLabelSlot(getText("psd_glass"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "apg_glass"), false);
    private final GuiLabelSlot psdDoorSlot = new GuiLabelSlot(getText("psd_door"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "apg_door"), false);
    private final GuiLabelButton pidsBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasPids = !pe.hasPids;
            b.setMessage(pe.hasPids ? getText("pids_on") : getText("pids_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("pids"));

    private final GuiLabelSlot pidBlockSlot = new GuiLabelSlot(getText("pids_block"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "pids_1"), false);
    private final GuiLabelSlot pidPoleSlot = new GuiLabelSlot(getText("pids_pole"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "pids_pole"), false);

    public int getSelectedIndex() {
        return canvas != null ? canvas.getSelectedIndex() : -1;
    }

    public StationEditorScreen(BlockPos pos, Direction facing, NbtCompound nbt) {
        super(getText("station_builder"));
        this.pos = pos;
        this.facing = facing;

        if (nbt != null) {
            if (nbt.contains("length")) this.initialLength = String.valueOf(nbt.getInt("length"));
            NbtList list = nbt.getList("elements", 10);
            for (int i = 0; i < list.size(); i++) {
                this.elements.add(StationElement.fromNbt(list.getCompound(i)));
            }
        }
    }

    protected void refreshPropertyArea() {
        int index = canvas.getSelectedIndex();
        trackProperties.setVisible(false);
        platformProperties.setVisible(false);
        buildingProperties.setVisible(false);
        emptyProperties.setVisible(false);

        boolean hasSelection = index != -1;
        delButton.setActive(hasSelection);
        moveLeftButton.setActive(hasSelection && index > 0);
        moveRightButton.setActive(hasSelection && index < elements.size() - 1);

        if (0 <= index && index < elements.size()) {
            StationElement e = this.elements.get(index);
            if (e instanceof TrackElement t) {
                trackProperties.setVisible(true);
                trackBallastField.setBlockId(t.ballastBlock);
                useMtrTrackButton.setMessage(t.isMtrTrack?getText("mtr_on"):getText("mtr_off"));
            } else if(e instanceof PlatformElement p) {
                platformProperties.setVisible(true);
                platformLengthField.setText(String.valueOf(p.width));
                platformSafetyField.setBlockId(p.safetyBlock);
                for(int i = 0; i < 5; i++) {
                    weightFields[i].setBlockId(p.mixSlots[i].blockId);
                    weightFields[i].setText(String.valueOf(p.mixSlots[i].weight));
                }
                hasCanopyBtn.setMessage(p.hasCanopy ? getText("canopy_on") : getText("canopy_off"));
                canopyHeightField.setText(String.valueOf(p.canopyHeight));
                canopySlabSlot.setBlockId(p.canopySlabId);
                canopyStyleBtn.setMessage(getText(p.canopyStyle.name()));

                hasLightingBtn.setMessage(p.hasLighting ? getText("lighting_on") : getText("lighting_off"));
                pillarStyleBtn.setMessage(getText(p.pillarStyle.name()));
                pillarBlockSlot.setBlockId(p.pillarBlockId);
                pillarSpacingField.setText(String.valueOf(p.pillarSpacing));
                pillarOffsetField.setText(String.valueOf(p.firstPillarOffset));

                shieldDoorBtn.setMessage(p.hasShieldDoors ? getText("psd_on") : getText("psd_off"));
                doorOffsetField.setText(String.valueOf(p.doorStartOffset));
                doorSpacingField.setText(String.valueOf(p.doorSpacing));
                psdEndSlot.setBlockId(p.psdEndId);
                psdGlassSlot.setBlockId(p.psdGlassId);
                psdDoorSlot.setBlockId(p.psdDoorId);

                pidsBtn.setMessage(p.hasPids ? getText("pids_on") : getText("pids_off"));
                pidBlockSlot.setBlockId(p.pidBlockId);
                pidPoleSlot.setBlockId(p.pidPoleId);
            } else if(e instanceof BuildingElement b) {
                buildingProperties.setVisible(true);
                buildingPresetField.setText(b.presetName);
            } else {
                emptyProperties.setVisible(true);
            }
        }
    }
    @Override
    protected void initControls() {
        int savedIndex = this.canvas != null ? this.canvas.getSelectedIndex() : -1;

        rootPanel.setGap(2);
        int width = this.width - (
                rootPanel.getMarginLeft() + rootPanel.getPaddingLeft() +
                rootPanel.getMarginRight() + rootPanel.getPaddingRight()
        );
        int height = this.height - (
                rootPanel.getMarginTop() + rootPanel.getMarginBottom() +
                rootPanel.getPaddingTop() + rootPanel.getPaddingBottom()
        );

        int topPanelHeight = 24;
        GuiPanel topPanel = new GuiPanel(width, topPanelHeight);
        lengthField.setText(initialLength);
        topPanel.addControl(lengthField);
        topPanel.addControl(new GuiButton(getText("presets"), b -> {
            MinecraftClient.getInstance().setScreen(new PresetSelectionScreen(this));
        }, BTN_WIDTH_XL, BTN_HEIGHT));
        topPanel.setMajorAlign(GuiPanel.MajorAlignMode.SPACE_BETWEEN);
        addControl(topPanel);

        this.canvas = new GuiRectCanvas(width, 34);
        SyncCanvasWithElements();
        if (savedIndex >= 0) {
            this.canvas.select(savedIndex);
        }
        addControl(canvas);

        int elemOpPanelHeight = 24;
        GuiPanel elemOpPanel = new GuiPanel(width, elemOpPanelHeight)
        .addControl(new GuiButton(getText("add_track"), b -> {
            var e = new TrackElement(); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "T");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_platform"), b -> {
            var e = new PlatformElement(); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "P");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_building"), b -> {
            var e = new BuildingElement("matchbox"); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "B");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT));

        delButton.setActive(false);
        moveLeftButton.setActive(false);
        moveRightButton.setActive(false);
        elemOpPanel.addControl(delButton).addControl(moveLeftButton).addControl(moveRightButton);

        addControl(elemOpPanel);

        int middlePanelHeight = height - elemOpPanelHeight - topPanelHeight * 2 - canvas.getHeight() - 4 * rootPanel.getGap();
        GuiPanel middlePanel = new GuiPanel(width, middlePanelHeight);

        int propertyPanelWidth = width - ghostInventory.getWidth() - middlePanel.getGap();
        GuiPanel middleLeftBox = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        middleLeftBox.setGap(0);

        trackProperties = getTrackProperties(propertyPanelWidth, middlePanelHeight);
        platformProperties = getPlatformProperties(propertyPanelWidth, middlePanelHeight);
        buildingProperties = getBuildingProperties(propertyPanelWidth, middlePanelHeight);
        emptyProperties = getEmptyProperties(propertyPanelWidth, middlePanelHeight);
        emptyProperties.setVisible(true);

        middleLeftBox.addControl(trackProperties)
                .addControl(platformProperties)
                .addControl(buildingProperties)
                .addControl(emptyProperties);

        GuiPanel middleRightBox = new GuiPanel(ghostInventory.getWidth(), middlePanelHeight);
        middleRightBox.setMajorAlign(GuiPanel.MajorAlignMode.END);
        middleRightBox.addControl(ghostInventory);

        middlePanel.addControl(middleLeftBox).addControl(middleRightBox);
        addControl(middlePanel);

        GuiPanel bottomPanel = new GuiPanel(width, topPanelHeight)
        .addControl(new GuiButton(getText("save_preset"), b -> {
            MinecraftClient.getInstance().setScreen(new PresetSaveScreen(this));
        }, BTN_WIDTH_L, BTN_HEIGHT))
        .addControl(new GuiButton(getText("construct"), b -> {
            sendBuildPacket(StationBuilder.BUILD_PACKET_ID); this.close();
        }, BTN_WIDTH_L, BTN_HEIGHT));

        bottomPanel.setMajorAlign(GuiPanel.MajorAlignMode.END);
        addControl(bottomPanel);

        refreshPropertyArea();
    }

    private void initProperties(GuiPanel panel) {
        panel.setPadding(10);
        panel.setGap(2);
        panel.setDirection(GuiPanel.PanelDirection.VERTICAL);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.START);
        panel.setBorderVisible(true);
        panel.setVisible(false);
    }

    private @NotNull GuiPanel getTrackProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel trackProperties = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(trackProperties);

        trackBallastField.slotChanged.clear();
        trackBallastField.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof TrackElement t) {
                t.ballastBlock = e.newId;
            }
        });

        trackProperties.addControl(trackBallastField).addControl(useMtrTrackButton);
        return trackProperties;
    }

    private @NotNull GuiPanel getPlatformProperties(int w, int h) {
        GuiTab p = new GuiTab(w, h);
        int w0 = p.getContentWidth(), h0 = p.getContentHeight();
        p.addTab(getText("platform"), getPlatformBase(w0, h0));
        p.addTab(getText("canopy"), getPlatformCanopy(w0, h0));
        p.addTab(getText("pillar"), getPlatformPillar(w0, h0));
        if (StationBuilder.isMtrLoaded()) {
            p.addTab(Text.literal("MTR"), getPlatformMtr(w0, h0));
        }
        p.setVisible(false);
        return p;
    }

    private @NotNull GuiPanel getPlatformMtr(int w, int h) {
        GuiPanel p = new GuiScrollablePanel(w, h);
        initProperties(p);

        psdEndSlot.slotChanged.clear();
        psdEndSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdEndId = e.newId;
            }
        });

        psdGlassSlot.slotChanged.clear();
        psdGlassSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdGlassId = e.newId;
            }
        });

        psdDoorSlot.slotChanged.clear();
        psdDoorSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdDoorId = e.newId;
            }
        });

        doorOffsetField.getTextField().textChanged.clear();
        doorOffsetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.doorStartOffset = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        doorSpacingField.getTextField().textChanged.clear();
        doorSpacingField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.doorSpacing = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        pidBlockSlot.slotChanged.clear();
        pidBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pidBlockId = e.newId;
            }
        });

        pidPoleSlot.slotChanged.clear();
        pidPoleSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pidPoleId = e.newId;
            }
        });

        // PIDs 开关
        p.addControl(shieldDoorBtn).addControl(doorOffsetField).addControl(doorSpacingField);
        p.addControl(psdEndSlot).addControl(psdGlassSlot).addControl(psdDoorSlot);
        p.addControl(pidsBtn).addControl(pidBlockSlot).addControl(pidPoleSlot);
        return p;
    }

    private @NotNull GuiPanel getPlatformBase(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel base = new GuiScrollablePanel(propertyPanelWidth, middlePanelHeight);
        initProperties(base);
        var topPanel = new GuiPanel(propertyPanelWidth, INPUT_HEIGHT);

        platformLengthField.getTextField().textChanged.clear();
        platformLengthField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p)
                try { p.width = Integer.parseInt(e.newText); } catch (Exception ex) {}
        });

        platformSafetyField.slotChanged.clear();
        platformSafetyField.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p) {
                p.safetyBlock = e.newId;
                autoRotate.setVisible(net.minecraft.registry.Registries.BLOCK.get(p.safetyBlock).
                        getDefaultState().contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING));
            }
        });

        autoRotate.setForeColor(0xFF00FF00);
        topPanel.addControl(platformLengthField).addControl(platformSafetyField).addControl(autoRotate);
        base.addControl(topPanel);
        for (int i = 0; i < 5; i++) {
            final int fieldIndex = i;
            weightFields[i] = new GuiLabelSlotInput(
                    net.minecraft.text.Text.translatable("gui.stationbuilder.platform_blocks", i + 1),
                    INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone"),
                    false, net.minecraft.text.Text.empty()
            );
            weightFields[i].getTextField().textChanged.addHandler((sender, e) -> {
                int selectedCanvasIndex = canvas.getSelectedIndex();
                if (selectedCanvasIndex >= 0 && elements.get(selectedCanvasIndex) instanceof PlatformElement p) {
                    try {
                        if (fieldIndex < p.mixSlots.length) {
                            p.mixSlots[fieldIndex].weight = Double.parseDouble(e.newText);
                        }
                    } catch (Exception ignored) {}
                }
            });
            // 处理从指针放进去的新方块同步
            weightFields[i].slotChanged.addHandler((sender, e) -> {
                int selectedCanvasIndex = canvas.getSelectedIndex();
                if (selectedCanvasIndex >= 0 && elements.get(selectedCanvasIndex) instanceof PlatformElement p) {
                    if (fieldIndex < p.mixSlots.length) {
                        p.mixSlots[fieldIndex].blockId = e.newId;
                    }
                }
            });
            base.addControl(weightFields[i]);
        }
        return base;
    }

    private @NotNull GuiPanel getPlatformPillar(int w0, int h0) {
        GuiPanel p = new GuiScrollablePanel(w0, h0);
        initProperties(p);

        pillarBlockSlot.slotChanged.clear();
        pillarBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pillarBlockId = e.newId;
            }
        });

        pillarSpacingField.getTextField().textChanged.clear();
        pillarSpacingField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.pillarSpacing = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        pillarOffsetField.getTextField().textChanged.clear();
        pillarOffsetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.firstPillarOffset = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        lightBlockSlot.slotChanged.clear();
        lightBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.lightBlockId = e.newId;
            }
        });

        p.addControl(pillarStyleBtn);
        p.addControl(pillarBlockSlot);
        p.addControl(pillarSpacingField);
        p.addControl(pillarOffsetField);
        p.addControl(hasLightingBtn);
        p.addControl(lightBlockSlot);
        return p;
    }

    private @NotNull GuiPanel getPlatformCanopy(int w0, int h0) {
        GuiPanel p = new GuiScrollablePanel(w0, h0);
        initProperties(p);

        canopyHeightField.getTextField().textChanged.clear();
        canopyHeightField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.canopyHeight = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        canopySlabSlot.slotChanged.clear();
        canopySlabSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.canopySlabId = e.newId;
            }
        });

        p.addControl(hasCanopyBtn);
        p.addControl(canopyHeightField);
        p.addControl(canopySlabSlot);
        p.addControl(canopyStyleBtn);

        return p;
    }

    private @NotNull GuiPanel getBuildingProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel buildingProperties = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(buildingProperties);

        buildingPresetField.getTextField().textChanged.clear();
        buildingPresetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof BuildingElement b)
                b.presetName = e.newText;
        });

        // ---- 添加“浏览”按钮（打开列表屏幕） ----
        GuiButton browseButton = new GuiButton(
                net.minecraft.text.Text.translatable("gui.stationbuilder.browse"),
                b -> {
                    if (client != null) {
                        client.setScreen(new BuildingSelectionScreen(this, buildingPresetField.getTextField()));
                    }
                },
                60, INPUT_HEIGHT
        );
        // ---- 添加“导入文件”按钮 ----
        GuiButton importButton = new GuiButton(
                net.minecraft.text.Text.translatable("gui.stationbuilder.import_file"),
                b -> openFileChooser(),
                60, INPUT_HEIGHT
        );
        buildingProperties.addControl(buildingPresetField);
        buildingProperties.addControl(browseButton);
        buildingProperties.addControl(importButton);
        return buildingProperties;
    }

    public void openFileChooser() {
        String path;

        // 使用 LWJGL 的 MemoryStack 分配内存，避免内存泄漏
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            // 分配指针缓冲区来存放过滤规则
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.flip();

            // 调用 TinyFileDialogs 打开原生文件选择器 (注意: 会阻塞当前线程直到对话框关闭)
            path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Structure File", // 对话框标题
                    null,                    // 默认路径
                    filters,                 // 过滤器
                    "Structure files (*.nbt, *.schem, *.schematic, *.litematic)", // 过滤器描述
                    false                    // 是否允许多选
            );
        }

        if (path != null) {
            java.io.File file = new java.io.File(path);

            // 使用异步去加载结构文件，防止卡死主线程太久
            java.util.concurrent.CompletableFuture.supplyAsync(() -> loadStructureFromFile(file))
                    .thenAcceptAsync(template -> {
                        if (template != null) {
                            String name = file.getName().replaceFirst("\\.[^.]+$", "");
                            BuildingTemplateManager.addTemplate(name, template);

                            // 更新文本框
                            buildingPresetField.setText(name);
                            // 更新当前选中的建筑元素
                            int index = canvas.getSelectedIndex();
                            if (index >= 0 && elements.get(index) instanceof BuildingElement b) {
                                b.presetName = name;
                                SyncCanvasWithElements();
                            }
                            if (client != null && client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text.translatable("gui.stationbuilder.import_success", name), false);
                            }
                        } else {
                            if (client != null && client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text.translatable("gui.stationbuilder.import_fail"), true);
                            }
                        }
                    }, client::execute);
        }
    }

    private StructureTemplate loadStructureFromFile(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".nbt")) {
                NbtCompound nbt = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                StructureTemplate template = new StructureTemplate();
                template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
                return template;
            } else if (name.endsWith(".schem") || name.endsWith(".schematic")) {
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else if (name.endsWith(".litematic")) {
                // Litematic 也可用 schematic4j 读取（SchematicReader 自动识别）
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else {
                return null;
            }
        } catch (Exception e) {
            StationBuilder.LOGGER.error("Failed to load structure file: {}", file, e);
            return null;
        }
    }

    private @NotNull GuiPanel getEmptyProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel panel = new  GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(panel);
        panel.setMajorAlign(GuiPanel.MajorAlignMode.CENTER);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);
        panel.addControl(new GuiLabel(getText("please_select")));
        return panel;
    }

    private void SyncCanvasWithElements() {
        int oldIndex = this.canvas.getSelectedIndex();
        canvas.clear();
        for (var element : elements) {
            if (element instanceof PlatformElement p) {
                canvas.addRect(p.getWidth() * 3, "P");
            } else if (element instanceof BuildingElement b) {
                canvas.addRect(b.getWidth() * 3, "B");
            } else if (element instanceof TrackElement t) {
                canvas.addRect(t.getWidth() * 3, "T");
            }
        }
        if (oldIndex >= 0) {
            canvas.select(oldIndex);
        }
    }

    public void applyPreset(PresetManager.PresetData data) {
        if (data == null) return;
        this.elements.clear();
        this.elements.addAll(data.elements());
        if (this.lengthField != null) {
            this.lengthField.setText(String.valueOf(data.length()));
        }
        SyncCanvasWithElements();
        refreshPropertyArea();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int oldIndex = canvas.getSelectedIndex();
        boolean ret = super.mouseClicked(mouseX, mouseY, button);

        // 当发生点击并在 Canvas 内选中新块或空白时，更新右侧的属性区域即可
        if (oldIndex != canvas.getSelectedIndex()) {
            refreshPropertyArea();
        }
        return ret;
    }

    @Override
    public void close() {
        // 关闭时自动发送保存包
        sendSyncPacket(SAVE_DATA_PACKET);
        super.close();
    }

    private void sendSyncPacket(Identifier packetId) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeInt(facing.getHorizontal());
            buf.writeInt(Integer.parseInt(lengthField.getText()));
            buf.writeInt(elements.size());
            for (StationElement e : elements) e.write(buf);
            ClientPlayNetworking.send(packetId, buf);
        } catch (Exception ignored) {}
    }

    private void sendBuildPacket(Identifier packetId) {
        sendSyncPacket(packetId);
    }

    public int getStationLength() {
        try {
            return Integer.parseInt(this.lengthField.getText());
        } catch (NumberFormatException e) {
            return Integer.parseInt(initialLength); // 默认值
        }
    }

    public List<StationElement> getElements() {
        return this.elements;
    }
}

```

## client\java\cn\myfrank\stationbuilder\gui\Event.java

```java
package cn.myfrank.stationbuilder.gui;

import java.util.ArrayList;
import java.util.List;


// 事件类封装
public class Event<T extends EventArgs> {
    private List<EventHandler<T>> handlers = new ArrayList<>();
    
    // 添加事件处理器
    public void addHandler(EventHandler<T> handler) {
        handlers.add(handler);
    }
    
    // 移除事件处理器
    public void removeHandler(EventHandler<T> handler) {
        handlers.remove(handler);
    }
    
    // 触发事件
    public void fire(Object sender, T e) {
        // 复制列表以防止在迭代过程中修改
        List<EventHandler<T>> copy = new ArrayList<>(handlers);
        for (EventHandler<T> handler : copy) {
            handler.handle(sender, e);
        }
    }
    
    // C#风格的事件调用（invoke）
    public void invoke(Object sender, T e) {
        fire(sender, e);
    }
    
    // 清空所有处理器
    public void clear() {
        handlers.clear();
    }
    
    // 获取处理器数量
    public int count() {
        return handlers.size();
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\EventArgs.java

```java
package cn.myfrank.stationbuilder.gui;

public class EventArgs {
    public static final EventArgs EMPTY = new EventArgs();
}
```

## client\java\cn\myfrank\stationbuilder\gui\EventHandler.java

```java
package cn.myfrank.stationbuilder.gui;

@FunctionalInterface
public interface EventHandler<T extends EventArgs> {
    void handle(Object sender, T e);
}
```

## client\java\cn\myfrank\stationbuilder\gui\GhostInventory.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public class GhostInventory extends GuiControl {
    protected static final int SLOT_SIZE = 18;
    protected MinecraftClient client = MinecraftClient.getInstance();

    public GhostInventory() {
        super(0, 0, 172, 80);
    }

    private void drawSlotBackground(DrawContext context, int x, int y) {
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
        context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF373737);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            var inv = client.player.getInventory();
            for (int i = 0; i < 36; i++) {
                int slotX = x + (i % 9) * SLOT_SIZE;
                int slotY = y + (i < 9 ? 58 : (i / 9 - 1) * SLOT_SIZE);

                drawSlotBackground(context, slotX, slotY);
                context.drawItem(inv.getStack(i), slotX + 1, slotY + 1);

                // 悬停高亮
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x88FFFFFF);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && isMouseOver(mouseX, mouseY)) {
            int clickedIndex = -1;
            for (int i = 0; i < 36; i++) {
                int slotX = x + (i % 9) * SLOT_SIZE;
                int slotY = y + (i < 9 ? 58 : (i / 9 - 1) * SLOT_SIZE);
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    clickedIndex = i;
                    break;
                }
            }

            GuiScreen screen = (GuiScreen) client.currentScreen;
            if (screen != null && clickedIndex != -1) {
                ItemStack stack = client.player.getInventory().getStack(clickedIndex);
                if (button == 0) { // 左键
                    if (screen.cursorStack.isEmpty() && !stack.isEmpty()) { // 拿起
                        screen.cursorStack = stack.copy();
                    } else if (!screen.cursorStack.isEmpty()) { // 放下/清空指针
                        screen.cursorStack = ItemStack.EMPTY;
                    }
                } else if (button == 1 || button == 2) { // 右/中键清空
                    screen.cursorStack = ItemStack.EMPTY;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GhostSlot.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class GhostSlot extends GuiControl implements GhostSlotLike {
    protected static final int SLOT_SIZE = 18;
    private Identifier blockId;

    public class BlockIdChangingEventArgs extends EventArgs {
        public final Identifier newId;
        public boolean canceled = false;
        public BlockIdChangingEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }

    public class BlockIdChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public BlockIdChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }

    public final Event<BlockIdChangingEventArgs> blockIdChanging = new Event<>();
    public final Event<BlockIdChangedEventArgs> blockIdChanged = new Event<>();

    public GhostSlot(Identifier blockId, boolean active) {
        super(0, 0, SLOT_SIZE, SLOT_SIZE);
        this.blockId = blockId;
    }

    public void setBlockId(Identifier blockId, boolean invokeEvent) {
        if (invokeEvent) {
            var eventArgs = new BlockIdChangingEventArgs(blockId);
            blockIdChanging.invoke(this, eventArgs);
            if (eventArgs.canceled) return;
        }
        this.blockId = blockId;
        if (invokeEvent) {
            blockIdChanged.invoke(this, new BlockIdChangedEventArgs(blockId));
        }
    }

    @Override
    public void setBlockId(Identifier blockId) {
        setBlockId(blockId, true);
    }

    public Identifier getBlockId() {
        return this.blockId;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            int borderColor = hovered ? 0xFFFFFFFF : 0xFF8B8B8B;
            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF222222);

            var t = Registries.ITEM.get(blockId);
            context.drawItem(new ItemStack(t == Items.AIR ? Registries.BLOCK.get(blockId): t), x + 1, y + 1);
            if (hovered) {
                context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x88FFFFFF); // 高亮
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            GuiScreen screen = (GuiScreen) MinecraftClient.getInstance().currentScreen;
            if (screen != null) {
                if (!screen.cursorStack.isEmpty()) {
                    Identifier newId = Registries.ITEM.getId(screen.cursorStack.getItem());
                    if (screen.cursorStack.getItem() instanceof net.minecraft.item.BlockItem bi) {
                        newId = Registries.BLOCK.getId(bi.getBlock());
                    }
                    setBlockId(newId);
                } else if (button == 1 || button == 2) {
                    setBlockId(new Identifier("minecraft", "air"));
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GhostSlotLike.java

```java
package cn.myfrank.stationbuilder.gui;

public interface GhostSlotLike {
    void setBlockId(net.minecraft.util.Identifier blockId);
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiButton.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class GuiButton extends GuiControl {
    // 替换 ButtonWidget.PressAction
    public interface PressAction {
        void onPress(GuiButton button);
    }

    protected Text message;
    protected PressAction onPress;
    protected Tooltip tooltip;
    protected boolean active = true; // 对应原版 active 状态
    protected boolean hovered = false; // 对应原版 isMouseOver 状态
    protected TextRenderer textRenderer;

    public GuiButton(Text message, PressAction onPress, int width, int height) {
        super(0, 0, width, height);
        this.message = message;
        this.onPress = onPress;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    public GuiButton(Text message, PressAction onPress, int width, int height, Tooltip tooltip) {
        this(message, onPress, width, height);
        this.tooltip = tooltip;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }

        this.hovered = isMouseOver(mouseX, mouseY); // 更新悬停状态

        int backgroundColor = active ? (hovered ? 0xFF5555FF : 0xFF3333CC) : 0xFF888888; // 蓝色系，悬停变亮，禁用变灰
        int borderColor = active ? (hovered ? 0xFFFFFFFF : 0xFFAAAAAA) : 0xFF555555;
        int textColor = active ? (hovered ? 0xFFFFFF00 : 0xFFFFFFFF) : 0xFFAAAAAA; // 文本颜色，悬停变黄，禁用变灰

        // 绘制背景
        context.fill(x, y, x + w, y + h, backgroundColor);
        // 绘制边框
        context.drawBorder(x, y, w, h, borderColor);

        // 绘制文本
        int textWidth = textRenderer.getWidth(message);
        int textHeight = textRenderer.fontHeight;
        context.drawText(
                textRenderer,
                message,
                x + (w - textWidth) / 2,
                y + (h - textHeight) / 2,
                textColor,
                false
        );

        // 渲染 tooltip
        if (hovered && tooltip != null) {
            tooltip.render(hovered, focused, getNavigationFocus());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && active && isMouseOver(mouseX, mouseY) && button == 0) { // 检查可见性、激活状态、鼠标位置和左键
            if (onPress != null) {
                onPress.onPress(this);
            }
            return true;
        }
        return false;
    }

    // 其他事件处理方法可以简化或直接继承 GuiControl 的默认实现
    // 对于完全自定义的 GuiControl，通常不需要实现所有 Element 接口的方法
    // 这里只保留了核心的 isMouseOver
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY);
    }

    // 由于不再继承 ButtonWidget，这些导航和焦点方法需要根据实际需求重新实现
    // 这里提供一个简化版本，它们不会真正处理复杂的导航
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        // 可以根据 focused 状态改变按钮的渲染样式，例如加粗边框
    }

    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        if (isFocused()) {
            return GuiNavigationPath.of(this);
        }
        return null;
    }

    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        if (this.active && this.isVisible()) {
            return GuiNavigationPath.of(this);
        }
        return null;
    }

    @Override
    public ScreenRect getNavigationFocus() {
        return new ScreenRect(this.x, this.y, this.w, this.h);
    }

    @Override
    public boolean isNarratable() {
        return this.isVisible() && this.active; // 仅当可见且激活时才可叙述
    }

    @Override
    public int getNavigationOrder() {
        return 0; // 默认导航顺序，可能需要更复杂的逻辑来指定
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiControl.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import org.jetbrains.annotations.Nullable;

public abstract class GuiControl implements GuiControlLike {
    public GuiControl(int x, int y, int w, int h) {
        this.x = x; this.y = y;
        this.w = w; this.h = h;
    }

    protected GuiControlLike parent = null;
    @Override public @Nullable GuiControlLike getParent() {return parent;}
    @Override public void setParent(GuiControlLike parent) {this.parent = parent;}

    protected int x, y, w, h;
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    protected boolean focused = false, visible = true;
    @Override public void setFocused(boolean focused) {
        this.focused = focused;
    }
    @Override public boolean isFocused() {
        return focused;
    }
    public void setVisible(boolean visible) { this.visible = visible; }

    @Override public boolean isVisible() {
        boolean this_visible = visible;
        GuiControlLike p = parent;
        while(this_visible && p != null) {
            this_visible = p.isVisible();
            p = p.getParent();
        }
        return this_visible;
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return x <= mouseX && mouseX <= x + w && y <= mouseY && mouseY <= y + h;
    }
    @Override public SelectionType getType() {
        return SelectionType.NONE;
    }
    @Override public void appendNarrations(NarrationMessageBuilder builder) {}
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiControlLike.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import org.jetbrains.annotations.Nullable;

public interface GuiControlLike extends Drawable, Element, Selectable {
    int getWidth();
    int getHeight();
    void setX(int x);
    void setY(int y);
    int getX();
    int getY();
    default void setPosition(int x, int y) {
        setX(x); setY(y);
    }
    boolean isVisible();
    void setVisible(boolean visible);

    @Nullable
    GuiControlLike getParent();

    void setParent(@Nullable GuiControlLike parent);
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiLabel.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class GuiLabel extends GuiControl {
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected Text text;
    protected int foreColor = 0xFFFFFF;
    public GuiLabel(int x, int y, Text text) {
        super(x, y, 0, 0);
        setText(text);
    }
    public GuiLabel(Text text) {
        super(0, 0, 0, 0);
        setText(text);
    }
    public int getForeColor() { return foreColor; }
    public void setForeColor(int color) { foreColor = color; }
    public void setText(Text text) {
        this.text = text;
        w = textRenderer.getWidth(text);
        h = textRenderer.getWrappedLinesHeight(text, w + 1);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (isVisible()) {
            context.drawText(textRenderer, text, x, y, foreColor, false);
        }
    }
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiLabelButton.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class GuiLabelButton extends GuiPanel {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GuiButton button;

    public GuiLabel getLabel() { return label; }
    public GuiButton getButton() { return button; }
    public void setMessage(Text msg) {
        button.setMessage(msg);
    }

    public GuiLabelButton(Text buttonMessage, GuiButton.PressAction onPress, int buttonWidth, int buttonHeight, Text label) {
        super(buttonWidth, buttonHeight);
        this.label = new GuiLabel(label);
        this.button = new GuiButton(buttonMessage, onPress, buttonWidth, buttonHeight);
        this.addControl(this.label).addControl(this.button);
        calcSize();
    }

    protected void calcSize() {
        w = label.w + getGap() + button.getWidth();
        h = Math.max(label.h, button.getHeight());
    }
    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiLabelSlot.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiLabelSlot extends GuiPanel implements GhostSlotLike {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;

    public class SlotChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public SlotChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlot(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.addControl(this.label).addControl(this.ghostSlot);
        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.slotChanged.invoke(this, new SlotChangedEventArgs(e.newId));
        });
        calcSize();
    }

    public Identifier getBlockId() {
        return ghostSlot.getBlockId();
    }

    @Override
    public void setBlockId(Identifier blockId) {
        this.ghostSlot.setBlockId(blockId);
    }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap();
        h = Math.max(label.h, ghostSlot.h);
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiLabelSlotInput.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiLabelSlotInput extends GuiPanel implements GhostSlotLike {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GhostSlot ghostSlot;
    protected GuiTextField textField;

    public class SlotChangedEventArgs extends EventArgs {
        public final Identifier newId;
        public SlotChangedEventArgs(Identifier newId) {
            this.newId = newId;
        }
    }
    public final Event<SlotChangedEventArgs> slotChanged = new Event<>();

    public GuiLabelSlotInput(Text label, int textFieldWidth, int textFieldHeight, Identifier blockId, boolean active, Text text) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.ghostSlot = new GhostSlot(blockId, active);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, text);
        this.addControl(this.label).addControl(this.ghostSlot).addControl(this.textField);

        this.ghostSlot.blockIdChanged.addHandler((sender, e) -> {
            this.slotChanged.invoke(this, new SlotChangedEventArgs(e.newId));
        });

        calcSize();
    }

    public GuiLabel getLabel() { return label;}
    public GhostSlot getGhostSlot() { return ghostSlot; }
    public GuiTextField getTextField() { return textField; }
    public String getText() { return textField.getText(); }
    public void setText(String text) { textField.setText(text);}
    public Identifier getBlockId() { return ghostSlot.getBlockId(); }

    @Override
    public void setBlockId(Identifier blockId) { this.ghostSlot.setBlockId(blockId); }

    protected void calcSize() {
        w = label.w + getGap() + ghostSlot.w + getGap() + textField.getWidth();
        h = Math.max(label.h, Math.max(ghostSlot.h, textField.getHeight()));
    }

    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiLabelTextField.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

public class GuiLabelTextField extends GuiPanel {
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    protected GuiLabel label;
    protected GuiTextField textField;

    public GuiLabel getLabel() { return label; }
    public GuiTextField getTextField() { return textField; }
    public String getText() {
        return textField.getText();
    }
    public void setText(String text) {
        textField.setText(text);
    }

    public GuiLabelTextField(Text label, int textFieldWidth, int textFieldHeight, Text field) {
        super(textFieldWidth, textFieldHeight);
        this.label = new GuiLabel(label);
        this.textField = new GuiTextField(textRenderer, textFieldWidth, textFieldHeight, field);
        this.addControl(this.label).addControl(this.textField);
        calcSize();
    }

    protected void calcSize() {
        w = label.w + getGap() + textField.getWidth();
        h = Math.max(label.h, textField.getHeight());
    }
    public void setLabelText(Text text) {
        label.setText(text);
        calcSize();
    }
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiPanel.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class GuiPanel extends GuiControl {
    public enum PanelDirection {
        HORIZONTAL, VERTICAL
    }
    public enum MajorAlignMode {
        START, CENTER, END, SPACE_BETWEEN
    }
    public enum CrossAlignMode {
        START, CENTER, END
    }
    protected int gap = 5;
    protected MajorAlignMode majorAlign = MajorAlignMode.START;
    protected CrossAlignMode crossAlign = CrossAlignMode.CENTER;
    protected int marginLeft = 0, marginRight = 0, marginTop = 0, marginBottom = 0;
    protected int paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;
    protected boolean showBorder = false, showBackground = false;
    protected PanelDirection direction =  PanelDirection.HORIZONTAL;
    protected List<GuiControlLike> controls = new ArrayList<>();

    public void setMargin(int margin) {
        marginLeft = margin; marginRight = margin; marginTop = margin; marginBottom = margin;
    }
    public void setPadding(int padding) {
        paddingLeft = padding; paddingRight = padding; paddingTop = padding; paddingBottom = padding;
    }
    public void setMargin(int marginLeft, int marginRight, int marginTop, int marginBottom) {
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
    }
    public void setPadding(int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
    }
    public void setMarginLeft(int marginLeft) { this.marginLeft = marginLeft; }
    public int getMarginLeft() { return  marginLeft; }
    public void setMarginRight(int marginRight) { this.marginRight = marginRight; }
    public int getMarginRight() { return  marginRight; }
    public void setMarginTop(int marginTop) { this.marginTop = marginTop; }
    public int getMarginTop() { return  marginTop; }
    public void setMarginBottom(int marginBottom) { this.marginBottom = marginBottom; }
    public int getMarginBottom() { return  marginBottom; }
    public void setPaddingLeft(int paddingLeft) { this.paddingLeft = paddingLeft; }
    public int getPaddingLeft() { return  paddingLeft; }
    public void setPaddingRight(int paddingRight) { this.paddingRight = paddingRight; }
    public int getPaddingRight() { return  paddingRight; }
    public void setPaddingTop(int paddingTop) { this.paddingTop = paddingTop; }
    public int getPaddingTop() { return  paddingTop; }
    public void setPaddingBottom(int paddingBottom) { this.paddingBottom = paddingBottom; }
    public int getPaddingBottom() { return  paddingBottom; }

    public int getGap() { return gap; }
    public void setGap(int gap) { this.gap = gap; }

    public boolean isBorderVisible() { return showBorder; }
    public void setBorderVisible(boolean showBorder) { this.showBorder = showBorder; }
    public PanelDirection getDirection() { return direction; }
    public void setBackgroundVisible(boolean showBackground) { this.showBackground = showBackground; }
    public boolean isBackgroundVisible() { return showBackground; }
    public void setDirection(PanelDirection direction) { this.direction = direction; }
    public MajorAlignMode getMajorAlign() { return majorAlign; }
    public void setMajorAlign(MajorAlignMode majorAlign) { this.majorAlign = majorAlign; }
    public CrossAlignMode getCrossAlign() { return crossAlign; }
    public void setCrossAlign(CrossAlignMode crossAlign) { this.crossAlign = crossAlign; }

    public GuiPanel(int width, int height) {
        super(0, 0, width, height);
    }

    public GuiPanel addControl(GuiControlLike control) {
        controls.add(control);
        control.setParent(this);
        return this;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            for (GuiControlLike control : controls) {
                control.setFocused(false);
            }
        }
        super.setFocused(focused);
    }

    public List<GuiControlLike> doLayout() {
        int panelContentX = x + marginLeft + paddingLeft;
        int panelContentY = y + marginTop + paddingTop;
        int panelRenderWidth = this.w; // GuiPanel 自身的宽度
        int panelRenderHeight = this.h; // GuiPanel 自身的高度

        // 计算主轴方向上所有可见控件的总尺寸（包括 gap）
        int totalMajorSize = 0;
        int maxCrossSize = 0; // 交叉轴上最大的可见控件尺寸
        List<GuiControlLike> visibleControls = new ArrayList<>(); // 存储可见的控件以便后续布局

        if (direction == PanelDirection.HORIZONTAL) {
            for (GuiControlLike control : controls) {
                if (!control.isVisible()) continue;
                visibleControls.add(control);
                totalMajorSize += control.getWidth();
                maxCrossSize = Math.max(maxCrossSize, control.getHeight());
            }
        } else { // VERTICAL
            for (GuiControlLike control : controls) {
                if (!control.isVisible()) continue;
                visibleControls.add(control);
                totalMajorSize += control.getHeight();
                maxCrossSize = Math.max(maxCrossSize, control.getWidth());
            }
        }

        // 如果没有可见的控件，则无需继续布局和渲染
        if (visibleControls.isEmpty()) {
            return visibleControls;
        }

        // 计算主轴起始位置
        int majorStartPos = 0, majorGap = gap;
        if (direction == PanelDirection.HORIZONTAL) {
            int availableMajorSpace = panelRenderWidth - paddingLeft - paddingRight;
            if(majorAlign == MajorAlignMode.SPACE_BETWEEN && visibleControls.size() > 1) { // 使用 visibleControls.size()
                majorGap = (availableMajorSpace - totalMajorSize) / (visibleControls.size() - 1);
            }else {
                totalMajorSize += (visibleControls.size() - 1) * gap; // 使用 visibleControls.size()
            }
            majorStartPos = switch (majorAlign) {
                case START, SPACE_BETWEEN -> panelContentX;
                case CENTER -> panelContentX + (availableMajorSpace - totalMajorSize) / 2;
                case END -> panelContentX + availableMajorSpace - totalMajorSize;
            };

        } else { // VERTICAL
            int availableMajorSpace = panelRenderHeight - paddingTop - paddingBottom;
            if(majorAlign == MajorAlignMode.SPACE_BETWEEN && visibleControls.size() > 1) { // 使用 visibleControls.size()
                majorGap = (availableMajorSpace - totalMajorSize) / (visibleControls.size() - 1);
            }else {
                totalMajorSize += (visibleControls.size() - 1) * gap; // 使用 visibleControls.size()
            }
            majorStartPos = switch (majorAlign) {
                case START, SPACE_BETWEEN -> panelContentY;
                case CENTER -> panelContentY + (availableMajorSpace - totalMajorSize) / 2;
                case END -> panelContentY + availableMajorSpace - totalMajorSize;
            };
        }

        int currentMajorPos = majorStartPos;
        for (GuiControlLike control : visibleControls) {
            int cx = 0, cy = 0;
            if (direction == PanelDirection.HORIZONTAL) {
                cx = currentMajorPos;
                // 计算交叉轴起始位置
                int availableCrossSpace = panelRenderHeight - paddingTop - paddingBottom;
                cy = switch (crossAlign) {
                    case START -> panelContentY;
                    case CENTER -> panelContentY + (availableCrossSpace - control.getHeight()) / 2;
                    case END -> panelContentY + availableCrossSpace - control.getHeight();
                };
                currentMajorPos += control.getWidth() + majorGap;
            } else { // VERTICAL
                cy = currentMajorPos;
                // 计算交叉轴起始位置
                int availableCrossSpace = panelRenderWidth - paddingLeft - paddingRight;
                cx = switch (crossAlign) {
                    case START -> panelContentX;
                    case CENTER -> panelContentX + (availableCrossSpace - control.getWidth()) / 2;
                    case END -> panelContentX + availableCrossSpace - control.getWidth();
                };
                currentMajorPos += control.getHeight() + majorGap;
            }
            control.setPosition(cx, cy);
        }
        return visibleControls;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 首先检查面板自身的可见性
        if (!isVisible()) return;

        // 计算面板的实际内容区域（包括 padding，不包括 margin）
        int panelRenderWidth = this.w; // GuiPanel 自身的宽度
        int panelRenderHeight = this.h; // GuiPanel 自身的高度

        // 绘制背景和边框
        if (showBackground) {
            context.fill(
                    x + marginLeft,
                    y + marginTop,
                    x + marginLeft + panelRenderWidth,
                    y + marginTop + panelRenderHeight,
                    0x44000000
            );
        }
        if (showBorder) {
            context.drawBorder(
                    x + marginLeft,
                    y + marginTop,
                    panelRenderWidth,
                    panelRenderHeight,
                    0xFF8B8B8B
            );
        }

        if (controls.isEmpty()) { return; }

        context.enableScissor(x, y, x + w, y + h);
        for (GuiControlLike control : doLayout()) {
            control.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
    }

    // 递归处理鼠标点击：从后往前遍历（最上层优先）
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        GuiControlLike clickedControl = null;
        for (int i = controls.size() - 1; i >= 0; i--) {
            GuiControlLike control = controls.get(i);
            if (control.isVisible() && control.mouseClicked(mouseX, mouseY, button)) {
                clickedControl = control;
                break;
            }
        }
        if (clickedControl != null) {
            for (GuiControlLike control : controls) {
                if (control != clickedControl) {
                    control.setFocused(false);
                }
            }
            return true;
        }
        return false;
    }

    // 递归处理滚轮、键盘等
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        for (int i = controls.size() - 1; i >= 0; i--) {
            if (controls.get(i).isVisible() && controls.get(i).mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (GuiControlLike control : controls) {
            if (control.isVisible() && control.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (GuiControlLike control : controls) {
            if (control.isVisible() && control.charTyped(chr, modifiers)) return true;
        }
        return false;
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiRectCanvas.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;

public class GuiRectCanvas extends GuiControl{
    protected TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    public static class Rect {
        public int width;
        public String text;
        public Rect(int width, String text) {
            this.width = width;
            this.text = text;
        }
    }
    protected int selectedIndex = -1, gap = 4;
    protected ArrayList<Rect> rectangles = new ArrayList<>();

    public GuiRectCanvas(int w, int h) {
        super(0, 0, w, h);
    }

    public int getGap() {
        return gap;
    }
    public void setGap(int gap) {
        this.gap = gap;
    }
    public int getSelectedIndex() {
        return selectedIndex;
    }
    public void select(int index) {
        if (index >= 0 && index < rectangles.size()) {
            this.selectedIndex = index;
        }
    }
    public void clear() {
        rectangles.clear();
        clearSelection();
    }
    public void clearSelection() {
        this.selectedIndex = -1;
    }
    public void addRect(int width, String text) {
        rectangles.add(new Rect(width, text));
    }
    public void removeRect(int index) {rectangles.remove(index);}

    public void swap(int i, int j) {
        Collections.swap(rectangles, i, j);
        if (selectedIndex == i) {
            selectedIndex = j;
        } else if(selectedIndex == j) {
            selectedIndex = i;
        }
    }

    public void removeSelectedRect() {
        if (selectedIndex != -1) {
            rectangles.remove(selectedIndex);
            selectedIndex = -1;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        int cx = x + gap, cy = y + gap, dy = cy + h - 2 * gap;
        context.drawBorder(x, y, w, h, 0xFF888888);
        for(int i = 0; i < rectangles.size(); i++) {
            Rect rect = rectangles.get(i);
            context.fill(cx, cy, cx + rect.width, dy,
                    (i == selectedIndex) ? 0xFFFFFF00 : 0xFF888888);
            int tw = textRenderer.getWidth(rect.text);
            int th = textRenderer.getWrappedLinesHeight(rect.text, tw + 1);;
            context.drawText(
                    textRenderer, rect.text,
                    cx + (rect.width - tw) / 2,
                    cy + (dy - cy - th) / 2,
                    0xFF000000,false
            );
            cx += rect.width + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = x + gap, cy = y + gap, dy = cy + h - 2 * gap;
        // selectedIndex = -1;
        for(int i = 0; i < rectangles.size(); i++) {
            Rect rect = rectangles.get(i);
            if (cx <= mouseX && mouseX <= cx + rect.width &&
                cy <= mouseY && mouseY <= dy) {
                selectedIndex = i;
                break;
            }
            cx += rect.width + gap;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

```

## client\java\cn\myfrank\stationbuilder\gui\GuiScreen.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public abstract class GuiScreen extends Screen {
    protected GuiPanel rootPanel;
    public ItemStack cursorStack = ItemStack.EMPTY;

    public GuiScreen(Text title) {
        super(title);
        rootPanel = new GuiPanel(width, height);
    }
    public void addControl(GuiControlLike control) { rootPanel.addControl(control); }

    @Override
    protected void init() {
        this.children().clear();
        rootPanel.controls.clear();
        rootPanel.setMargin(0);
        rootPanel.setPadding(10);
        rootPanel.x = 0;
        rootPanel.y = 0;
        rootPanel.w = width;
        rootPanel.h = height;
        rootPanel.setDirection(GuiPanel.PanelDirection.VERTICAL);
        initControls();
        this.addDrawableChild(rootPanel);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        rootPanel.render(context, mouseX, mouseY, delta);

        // 渲染拿着的物品
        if (!cursorStack.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.drawItem(cursorStack, mouseX - 8, mouseY - 8);
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (rootPanel.mouseClicked(mouseX, mouseY, button)) return true;
        rootPanel.setFocused(false);
        // 若点击了空白处且手里拿着物品，则清空指针上的物品
        if (!cursorStack.isEmpty()) {
            cursorStack = ItemStack.EMPTY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (rootPanel.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rootPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (rootPanel.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rootPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    public void setDirection(GuiPanel.PanelDirection direction) {
        rootPanel.setDirection(direction);
    }
    public void setMajorAlign(GuiPanel.MajorAlignMode alignment) {
        rootPanel.setMajorAlign(alignment);
    }
    public void setCrossAlign(GuiPanel.CrossAlignMode alignment) {
        rootPanel.setCrossAlign(alignment);
    }
    protected abstract void initControls();

    @Override
    public boolean shouldPause() { return false; }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiScrollablePanel.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class GuiScrollablePanel extends GuiPanel {
    protected int scrollOffset = 0;
    protected int maxScroll = 0;
    protected int contentHeight = 0;

    public GuiScrollablePanel(int width, int height) {
        super(width, height);
        this.setBackgroundVisible(true);
        this.setBorderVisible(true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        if (showBackground) context.fill(x, y, x + w, y + h, 0x66000000);
        calculateContentHeight();
        maxScroll = Math.max(0, contentHeight - (h - paddingTop - paddingBottom));
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
        applyLayout();
        context.enableScissor(x, y, x + w, y + h);
        for (GuiControlLike control : controls) {
            if (!control.isVisible()) continue;
            control.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();
        if (maxScroll > 0) {
            int barWidth = 2;
            int barHeight = Math.max(10, (int) ((float) h * h / contentHeight));
            int barY = y + (int) ((float) scrollOffset / maxScroll * (h - barHeight));
            context.fill(x + w - barWidth - 1, barY, x + w - 1, barY + barHeight, 0xFFAAAAAA);
        }

        if (showBorder) context.drawBorder(x, y, w, h, 0xFF8B8B8B);
    }

    private void calculateContentHeight() {
        int totalHeight = paddingTop;
        for (GuiControlLike control : controls) {
            if (control.isVisible()) {
                totalHeight += control.getHeight() + gap;
            }
        }
        this.contentHeight = totalHeight + paddingBottom;
    }

    private void applyLayout() {
        int currentY = y + paddingTop - scrollOffset;
        for (GuiControlLike control : controls) {
            if (!control.isVisible()) continue;
            control.setX(x + paddingLeft);
            control.setY(currentY);
            currentY += control.getHeight() + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset -= (int) (verticalAmount * 15);
            return true;
        }
        return false;
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiTab.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class GuiTab extends GuiPanel {
    private final GuiPanel headerPanel;
    private final GuiPanel contentContainer;
    private final List<GuiControlLike> tabContents = new ArrayList<>();
    private final List<GuiButton> tabButtons = new ArrayList<>();
    private int selectedIndex = -1;

    // 样式配置
    private int headerHeight = 18;
    private int tabButtonWidth = 50;

    public int getContentWidth() { return contentContainer.getWidth(); }
    public int getContentHeight() { return contentContainer.getHeight(); }

    public GuiTab(int width, int height) {
        super(width, height);
        // 主面板设为垂直布局：上方是标题栏，下方是内容
        this.setDirection(PanelDirection.VERTICAL);
        this.setGap(2);

        // 初始化标题栏
        headerPanel = new GuiPanel(width, headerHeight);
        headerPanel.setDirection(PanelDirection.HORIZONTAL);
        headerPanel.setGap(2);
        headerPanel.setCrossAlign(CrossAlignMode.END); // 按钮对齐底部

        // 初始化内容容器
        contentContainer = new GuiPanel(width, height - headerHeight - getGap());
        contentContainer.setBorderVisible(true);
        contentContainer.setBackgroundVisible(true);
        contentContainer.setPadding(0);

        // 添加到主面板
        this.addControl(headerPanel);
        this.addControl(contentContainer);
    }

    /**
     * 添加一个选项卡
     * @param title 选项卡显示的文字
     * @param content 该选项卡对应的内容面板/控件
     */
    public void addTab(Text title, GuiControlLike content) {
        int index = tabContents.size();
        tabContents.add(content);

        // 创建切换按钮
        GuiButton button = new GuiButton(title, btn -> {
            selectTab(index);
        }, tabButtonWidth, headerHeight);

        tabButtons.add(button);
        headerPanel.addControl(button);

        // 将内容添加到容器中，初始设为不可见
        content.setVisible(false);
        contentContainer.addControl(content);

        // 如果是第一个添加的，默认选中
        if (selectedIndex == -1) {
            selectTab(0);
        }
    }

    /**
     * 切换到指定的选项卡
     */
    public void selectTab(int index) {
        if (index < 0 || index >= tabContents.size()) return;

        this.selectedIndex = index;

        for (int i = 0; i < tabContents.size(); i++) {
            boolean isSelected = (i == index);
            // 切换内容的可见性
            tabContents.get(i).setVisible(isSelected);
            // 切换按钮的状态（选中的按钮禁用，或者你可以自定义颜色）
            tabButtons.get(i).setActive(!isSelected);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setTabButtonWidth(int tabButtonWidth) {
        this.tabButtonWidth = tabButtonWidth;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先调用父类进行布局计算和渲染
        super.render(context, mouseX, mouseY, delta);
    }
}
```

## client\java\cn\myfrank\stationbuilder\gui\GuiTextField.java

```java
package cn.myfrank.stationbuilder.gui;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.function.Predicate;

public class GuiTextField extends GuiControl {
    public class TextChangedEventArgs extends EventArgs {
        public final String newText;
        public TextChangedEventArgs(String newText) {
            this.newText = newText;
        }
    }
    public final Event<TextChangedEventArgs> textChanged = new Event<>();
    protected TextRenderer textRenderer;
    protected String text;
    protected int maxLength = 32; // 默认最大长度
    protected boolean editable = true;
    protected Predicate<String> textPredicate = s -> true; // 文本验证器
    protected int textColor = 0xFFE0E0E0;
    protected int disabledTextColor = 0xFF707070;
    protected int backgroundColor = 0xFF000000;
    protected int borderColor = 0xFFA0A0A0;
    protected int focusedBorderColor = 0xFFFFFFA0;

    protected int selectionStart; // 文本选择开始位置
    protected int selectionEnd;   // 文本选择结束位置
    protected int cursor;         // 光标位置
    protected long lastBlinkTime;     // 光标闪烁计时
    protected boolean numberOnly = false; // 是否仅允许数字输入

    // 新增：文本滚动偏移量，表示文本内容向左滚动的像素值
    protected int scrollOffset = 0;

    // 文本内容区域的左右边距
    protected static final int TEXT_PADDING_X = 4;

    public GuiTextField(TextRenderer textRenderer, int width, int height, Text initialText) {
        super(0, 0, width, height); // x, y 设为 0，由 GuiPanel 布局
        this.textRenderer = textRenderer;
        this.text = initialText.getString();
        this.cursor = this.text.length();
        this.selectionStart = this.cursor;
        this.selectionEnd = this.cursor;
        this.h = height; // 确保高度被设置
        this.lastBlinkTime = System.currentTimeMillis(); // 初始化光标闪烁时间
        this.updateScrollOffset(); // 初始化滚动偏移量
    }

    public GuiTextField(TextRenderer textRenderer, int width, int height) {
        this(textRenderer, width, height, Text.empty());
    }

    public void setNumberOnly(boolean numberOnly) {this.numberOnly = numberOnly;}

    public boolean isNumberOnly() {return this.numberOnly;}

    public String getText() {return this.text;}

    public void setText(String newText) {
        setText(newText, true);
    }

    public void setText(String newText, boolean invokeEvent) {
        if (textPredicate.test(newText)) {
            if (newText.length() > maxLength) {
                newText = newText.substring(0, maxLength);
            }
            this.text = newText;
            // 确保光标位置在文本范围内
            this.cursor = Math.max(0, Math.min(this.cursor, this.text.length()));
            this.selectionStart = this.cursor;
            this.selectionEnd = this.cursor;
            this.lastBlinkTime = System.currentTimeMillis(); // 刷新闪烁
            this.updateScrollOffset(); // 更新滚动偏移
            if(invokeEvent) {
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
        }
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        // 如果当前文本超过新设定的最大长度，则截断
        if (this.text.length() > maxLength) {
            this.setText(this.text.substring(0, maxLength));
        }
    }

    public void setEditable(boolean editable) {this.editable = editable;}
    public void setTextColor(int textColor) {this.textColor = textColor;}
    public void setDisabledTextColor(int disabledTextColor) {this.disabledTextColor = disabledTextColor;}
    public void setTextPredicate(Predicate<String> textPredicate) {this.textPredicate = textPredicate;}
    // 移动光标
    protected void moveCursor(int offset) {this.setCursor(this.cursor + offset);}
    // 设置光标位置
    protected void setCursor(int newCursor) {
        newCursor = Math.max(0, Math.min(newCursor, this.text.length()));
        this.cursor = newCursor;
        this.selectionStart = newCursor;
        this.selectionEnd = newCursor;
        this.lastBlinkTime = System.currentTimeMillis();
        this.updateScrollOffset(); // 光标移动后更新滚动偏移
    }

    // 将光标设置到文本末尾
    protected void setCursorToEnd() {this.setCursor(this.text.length());}

    // 删除选择区域的文本
    protected void deleteSelectedText() {
        if (selectionStart != selectionEnd) {
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            this.text = new StringBuilder(this.text).delete(start, end).toString();
            this.setCursor(start); // 还原光标位置到删除区域的起点
            textChanged.invoke(this, new TextChangedEventArgs(this.text));
        }
    }

    // 插入文本
    protected void insertText(String insertion) {
        if (!editable) return;
        deleteSelectedText(); // 如果有选择区域，先删除
        StringBuilder builder = new StringBuilder(this.text);
        builder.insert(cursor, insertion);
        String newText = SharedConstants.stripInvalidChars(builder.toString()); // 过滤非法字符

        if (textPredicate.test(newText) && newText.length() <= maxLength) {
            this.text = newText;
            this.setCursor(this.cursor + insertion.length());
            textChanged.invoke(this, new TextChangedEventArgs(this.text));
        }
    }

    // 获取当前选中的文本
    public String getSelectedText() {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        return this.text.substring(start, end);
    }

    /**
     * 根据鼠标点击的X坐标计算光标应该放置的位置。
     * @param mouseXAbs 鼠标在屏幕上的X坐标
     * @return 光标位置（文本索引）
     */
    protected int getCursorPosFromMouseX(int mouseXAbs) {
        // 计算鼠标相对于文本渲染区域的局部X坐标
        int localMouseX = mouseXAbs - (x + TEXT_PADDING_X);

        // 将局部X坐标加上滚动偏移量，得到鼠标在完整文本中的“虚拟”X坐标
        int virtualMouseX = localMouseX + scrollOffset;

        // 获取完整文本的子字符串，以便计算宽度
        String fullText = this.text;
        int currentWidth = 0;
        for (int i = 0; i < fullText.length(); i++) {
            int charWidth = textRenderer.getWidth(fullText.substring(i, i + 1));
            if (virtualMouseX >= currentWidth && virtualMouseX < currentWidth + charWidth / 2) {
                return i;
            }
            if (virtualMouseX >= currentWidth + charWidth / 2 && virtualMouseX < currentWidth + charWidth) {
                return i + 1;
            }
            currentWidth += charWidth;
        }
        return fullText.length(); // 如果点击在文本末尾之后
    }

    protected void updateScrollOffset() {
        int textRenderWidth = w - TEXT_PADDING_X * 2; // 文本框内容的可用宽度

        if (this.text.isEmpty()) {
            this.scrollOffset = 0;
            return;
        }

        // 获取光标之前的文本宽度
        String textBeforeCursor = this.text.substring(0, cursor);
        int cursorPixelPos = textRenderer.getWidth(textBeforeCursor); // 光标在完整文本中的像素位置

        // 如果光标在可见区域左侧，则向左滚动
        if (cursorPixelPos < scrollOffset) {
            scrollOffset = cursorPixelPos;
        }
        // 如果光标在可见区域右侧，则向右滚动
        else if (cursorPixelPos > scrollOffset + textRenderWidth) {
            scrollOffset = cursorPixelPos - textRenderWidth;
        }

        // 确保滚动偏移量不会超出文本总宽度
        int totalTextWidth = textRenderer.getWidth(this.text);
        if (totalTextWidth < textRenderWidth) { // 文本没填满，不需要滚动
            scrollOffset = 0;
        } else {
            // 确保不会滚过头，导致右边出现空白
            scrollOffset = Math.min(scrollOffset, totalTextWidth - textRenderWidth);
            // 确保不会滚到负数
            scrollOffset = Math.max(0, scrollOffset);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;
        int currentTextColor = editable ? textColor : disabledTextColor;
        int currentBorderColor = isFocused() ? focusedBorderColor : borderColor;

        context.fill(x, y, x + w, y + h, backgroundColor);
        context.drawBorder(x, y, w, h, currentBorderColor);

        // 渲染文本和光标
        int textRenderAreaX = x + TEXT_PADDING_X;
        int textRenderAreaY = y + (h - textRenderer.fontHeight) / 2;
        int textRenderAreaWidth = w - TEXT_PADDING_X * 2; // 可用于渲染文本的宽度

        // 裁剪渲染区域，确保文本不会溢出文本框
        context.enableScissor(textRenderAreaX, y, textRenderAreaX + textRenderAreaWidth, y + h);
        String fullText = this.text;
        // 计算选中文本的渲染位置
        if (selectionStart != selectionEnd) {
            int selMin = Math.min(selectionStart, selectionEnd);
            int selMax = Math.max(selectionStart, selectionEnd);

            int selStartX = textRenderer.getWidth(fullText.substring(0, selMin));
            int selEndX = textRenderer.getWidth(fullText.substring(0, selMax));

            // 根据滚动偏移量调整渲染坐标
            context.fill(
                    textRenderAreaX + selStartX - scrollOffset,
                    textRenderAreaY - 1,
                    textRenderAreaX + selEndX - scrollOffset,
                    textRenderAreaY + textRenderer.fontHeight + 1,
                    0x880000FF // 半透明蓝色
            );
        }

        // 绘制文本
        context.drawText(
                textRenderer,
                fullText,
                textRenderAreaX - scrollOffset, // 应用滚动偏移
                textRenderAreaY,
                currentTextColor,
                false
        );

        // 绘制光标
        if (isFocused() && editable && ((System.currentTimeMillis() - lastBlinkTime) / 500) % 2 == 0) {
            int cursorPixelX = textRenderer.getWidth(fullText.substring(0, cursor));
            context.fill(
                    textRenderAreaX + cursorPixelX - scrollOffset, // 应用滚动偏移
                    textRenderAreaY - 1,
                    textRenderAreaX + cursorPixelX - scrollOffset + 1,
                    textRenderAreaY + textRenderer.fontHeight + 1,
                    currentTextColor
            );
        }
        context.disableScissor(); // 禁用裁剪
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isVisible() && isMouseOver(mouseX, mouseY) && button == 0) { // 左键点击
            setFocused(true);
            this.lastBlinkTime = System.currentTimeMillis();

            this.setCursor(getCursorPosFromMouseX((int)mouseX)); // 使用修正后的方法
            return true;
        }
        setFocused(false); // 点击到外面则失去焦点
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !editable) return false;
        lastBlinkTime = System.currentTimeMillis(); // 每次按键都重置光标闪烁

        // 处理方向键和删除键
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (hasSelection()) { // 如果有选中，光标移动到选择起点
                setCursor(Math.min(selectionStart, selectionEnd));
            } else {
                moveCursor(-1);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (hasSelection()) { // 如果有选中，光标移动到选择终点
                setCursor(Math.max(selectionStart, selectionEnd));
            } else {
                moveCursor(1);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_HOME) {
            setCursor(0);
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_END) {
            setCursorToEnd();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelectedText();
            } else if (cursor > 0) {
                this.text = new StringBuilder(this.text).deleteCharAt(cursor - 1).toString();
                this.setCursor(cursor - 1);
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelectedText();
            } else if (cursor < text.length()) {
                this.text = new StringBuilder(this.text).deleteCharAt(cursor).toString();
                this.setCursor(cursor); // 调用 setCursor 会更新滚动偏移 (光标位置不变)
                textChanged.invoke(this, new TextChangedEventArgs(this.text));
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+A 全选
            this.selectionStart = 0;
            this.selectionEnd = text.length();
            this.cursor = text.length(); // 光标也移到末尾
            this.updateScrollOffset(); // 更新滚动以确保选择区域可见
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+C 复制
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_X && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+X 剪切
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            deleteSelectedText();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { // Ctrl+V 粘贴
            String clipboardText = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboardText != null && !clipboardText.isEmpty()) insertText(clipboardText);
            return true;
        }
        return false;
    }
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isFocused() || !editable) return false;
        if (SharedConstants.isValidChar(chr) && (!numberOnly || Character.isDigit(chr))) {
            insertText(String.valueOf(chr));
            return true;
        }
        return false;
    }
    protected boolean hasSelection() {return selectionStart != selectionEnd;}
    @Override public void setPosition(int x, int y) {super.setPosition(x, y);}
    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        if (isFocused()) return GuiNavigationPath.of(this);
        return null;
    }
    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        if (this.editable && this.isVisible()) return GuiNavigationPath.of(this);
        return null;
    }
    @Override public ScreenRect getNavigationFocus() {return new ScreenRect(this.x, this.y, this.w, this.h);}
    @Override public boolean isNarratable() {return this.isVisible() && this.editable;}
}
```

## client\java\cn\myfrank\stationbuilder\mixin\client\StationBuilderMixinPlugin.java

```java
package cn.myfrank.stationbuilder.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class StationBuilderMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 如果 Mixin 类名包含 ".mtr."，则检查 mtr 是否加载
        if (mixinClassName.contains(".mtr.")) {
            return FabricLoader.getInstance().isModLoaded("mtr");
        }
        return true;
    }

    // 以下方法保持默认即可
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
```

## client\java\cn\myfrank\stationbuilder\mixin\client\mtr\RenderRailsMixin.java

```java
package cn.myfrank.stationbuilder.mixin.client.mtr;

import cn.myfrank.stationbuilder.RailBuilderItem;
import cn.myfrank.stationbuilder.StationBuilderBlock;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.mapper.PlayerHelper;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderRails.class)
public class RenderRailsMixin {
    @Inject(
            method = "isHoldingRailRelated",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void afterIsHoldingRailRelated(
            ClientPlayerEntity clientPlayerEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean original = cir.getReturnValue();

        // === 你的额外判断 ===
        boolean extra = isHoldingMyCustomRailThing(clientPlayerEntity);

        // === 整合结果 ===
        cir.setReturnValue(original || extra);
    }

    @Unique
    private static boolean isHoldingMyCustomRailThing(ClientPlayerEntity clientPlayerEntity) {
        return PlayerHelper.isHolding(new PlayerEntity(clientPlayerEntity.data), (item) ->
                item.data instanceof RailBuilderItem ||
                net.minecraft.block.Block.getBlockFromItem(item.data) instanceof StationBuilderBlock
        );
    }
}

```

