package cn.myfrank.stationbuilder.cpw;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CPWIntegration {
    public static final ResourceLocation WIRE_COIL = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "wire_coil");
    public static final ResourceLocation DEFAULT_MAST = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "lattice_mast");
    public static final ResourceLocation DEFAULT_CANTILEVER = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "cantilever_brown");

    private static final TagKey<Block> HEADSPAN_CONNECTABLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("pantographsandwires", "catenary_headspan_connectable")
    );

    /** 生成器使用的 headspan dropper 描述，不依赖 CPW 类型。 */
    public static final class HeadspanDropper {
        public final UUID id;
        public final double fraction;

        public HeadspanDropper(UUID id, double fraction) {
            this.id = id;
            this.fraction = fraction;
        }
    }

    /**
     * 将任意水平方向向量精确转换为对应的 CPWAngle 实例
     */
    public static CPWAngle getCPWAngles(Vec3 dir) {
        return CPWAngle.fromVector(dir);
    }

    public static BlockState applyMastProperties(BlockState state, CPWAngle angle, String part) {
        return applyMastProperties(state, angle.getFacing(), angle.getRotation(), part);
    }

    public static BlockState applyMastProperties(BlockState state, Direction facing, int rotation, String part) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("facing") && prop.getValueClass() == Direction.class) {
                @SuppressWarnings("unchecked")
                Property<Direction> dirProp = (Property<Direction>) prop;
                if (dirProp.getPossibleValues().contains(facing)) {
                    state = state.setValue(dirProp, facing);
                }
            } else if (prop.getName().equals("rotation") && prop.getValueClass() == Integer.class) {
                @SuppressWarnings("unchecked")
                Property<Integer> intProp = (Property<Integer>) prop;
                if (intProp.getPossibleValues().contains(rotation)) {
                    state = state.setValue(intProp, rotation);
                }
            } else if (part != null && prop.getName().equals("part")) {
                for (Comparable<?> val : prop.getPossibleValues()) {
                    if (val.toString().equalsIgnoreCase(part)) {
                        state = setUnchecked(state, prop, val);
                        break;
                    }
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setUnchecked(BlockState state, Property<?> prop, Comparable<?> val) {
        return state.setValue((Property<T>) prop, (T) val);
    }

    public static void placeMastBlock(ServerLevel world, BlockPos pos, CPWAngle angle, String part, ResourceLocation mastId) {
        // 已经存在的 mast 不重建：mast 上的节点/相邻腕臂导线依赖它，重建会丢掉旧的接触网。
        if (isMastBlockId(CommonUtil.getBlockId(world.getBlockState(pos).getBlock()))) return;
        BlockState base = CommonUtil.getBlockState(mastId);
        BlockState configured = applyMastProperties(base, angle, part);
        world.setBlock(pos, configured, 3);
    }

    /**
     * mast 底部不能踩在半砖上：如果下方是单半砖，提升为双层半砖（完整方块）。
     */
    public static void ensureFullBlockBelow(ServerLevel world, int x, int y, int z) {
        BlockPos below = new BlockPos(x, y - 1, z);
        BlockState state = world.getBlockState(below);
        if (state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)) {
            world.setBlock(below, state.setValue(SlabBlock.TYPE, SlabType.DOUBLE), 3);
        }
    }

    public static void buildMastColumn(ServerLevel world, int x, int z, int minY, int maxY, CPWAngle angle, ResourceLocation mastId) {
        if (minY > maxY) return;
        for (int y = minY; y <= maxY; y++) {
            String part = "in_between";
            if (y == minY && minY != maxY) part = "bottom";
            else if (y == maxY) part = "top";
            placeMastBlock(world, new BlockPos(x, y, z), angle, part, mastId);
        }
    }

    public static boolean placeCantilever(ServerLevel world, BlockPos cantileverPos, CPWAngle angle, ResourceLocation cantileverId) {
        // 复用已有腕臂及其 BlockEntity，避免重建导致旧节点/旧导线被移除。
        if (isCantileverBlockId(CommonUtil.getBlockId(world.getBlockState(cantileverPos).getBlock()))) {
            return true;
        }
        BlockState base = CommonUtil.getBlockState(cantileverId);
        BlockState configured = applyMastProperties(base, angle, null);
        world.setBlock(cantileverPos, configured, 3);
        return true;
    }

    /**
     * 沿铁轨延伸接触网导线。
     *
     * CPW 的 {@code pantographsandwires:wire_coil} 只是 MultiWireItem 的外壳，
     * 真正决定导线类型的是 subtype 数据组件；裸 ItemStack 会回退到 energy_wire。
     * 另外 {@code CatenaryWireItem.useWireOn} 会忽略右键目标，改为从玩家视线射线
     * 寻找 cantilever，因此在生成器里模拟 useOn 并不能可靠连线。
     *
     * 这里直接调用 CPW 的 WireGraph API，使用 CATENARY_WIRE 类型在两根 cantilever
     * 方块之间创建 edge。CPW 未加载或 API 版本不匹配时安全跳过。
     */
    public static void connectWire(ServerLevel world, Player player, BlockPos from, BlockPos to, ResourceLocation wireCoilId) {
        if (world == null || from == null || to == null || from.equals(to)) return;
        // 延后 1 tick，确保 cantilever 的 BlockEntity 已创建，WireGraph 能读取连接点。
        TickScheduler.schedule(1, () -> connectCatenaryWire(world, from, to));
    }

    public static boolean connectCatenaryWire(ServerLevel world, BlockPos fromCantilever, BlockPos toCantilever) {
        if (!ensureWireApi()) return false;
        try {
            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return false;

            Object customData = customDataCtor.newInstance(new CompoundTag());
            Object nodeA = blockConnectorNodeCtor.newInstance(fromCantilever);
            Object nodeB = blockConnectorNodeCtor.newInstance(toCantilever);
            Object pointIndex = mutableIntCtor.newInstance(0);
            Object result = wireGraphCreateEdge.invoke(
                    graph, catenaryWireType, customData, nodeA, nodeB, pointIndex, true
            );
            if (result == null) return false;
            return createEdgeResultSuccess == null
                    || Boolean.TRUE.equals(createEdgeResultSuccess.invoke(result));
        } catch (Throwable t) {
            logWireApiFailure(t);
            return false;
        }
    }

    // ========================================================
    // Headspan（多股道并行）
    // ========================================================

    /**
     * 选择可以用于 headspan 的支柱。必须属于
     * {@code pantographsandwires:catenary_headspan_connectable} 方块标签，
     * 否则 LatticeMastNodeData 无法创建节点。
     */
    public static ResourceLocation resolveHeadspanMastId(ResourceLocation configured) {
        if (configured != null && CommonUtil.getBlockState(configured).is(HEADSPAN_CONNECTABLE)) {
            return configured;
        }
        return DEFAULT_MAST;
    }

    /**
     * 在两根 headspan 支柱之间创建 CATENARY_HEADSPAN edge。
     * 四段高度信息以 CPW 的 UpperWireHeight / TopWireHeight / Droppers 自定义数据写入。
     */
    public static Object createHeadspanEdge(
            ServerLevel world, BlockPos mastA, BlockPos mastB,
            float upperWireHeight, float topWireHeight, List<HeadspanDropper> droppers
    ) {
        if (world == null || mastA == null || mastB == null || droppers == null || !ensureHeadspanApi()) {
            return null;
        }
        try {
            CompoundTag common = new CompoundTag();
            common.putFloat("UpperWireHeight", upperWireHeight);
            common.putFloat("TopWireHeight", topWireHeight);

            ListTag dropperList = new ListTag();
            for (HeadspanDropper dropper : droppers) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Id", dropper.id);
                tag.putDouble("Pos", dropper.fraction);
                dropperList.add(tag);
            }
            common.put("Droppers", dropperList);

            CompoundTag edgeData = new CompoundTag();
            edgeData.put("CustomData", common);
            edgeData.putInt("PointsCount", 2);

            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return null;

            Object customData = customDataCtor.newInstance(edgeData);
            Object nodeA = latticeMastNodeCtor.newInstance(mastA);
            Object nodeB = latticeMastNodeCtor.newInstance(mastB);

            // 如果同一对 mast 上已经存在 headspan，直接复用旧 edge，
            // 避免重建路段时生成重复结构，也保留旧接触网。
            Object hash = wireEdgeHashCtor.newInstance(customData, nodeA, nodeB);
            Object existing = wireGraphGetEdgeByHash.invoke(graph, hash);
            if (existing != null) {
                return existing;
            }

            Object pointIndex = mutableIntCtor.newInstance(0);
            Object result = wireGraphCreateEdge.invoke(
                    graph, headspanWireType, customData, nodeA, nodeB, pointIndex, true
            );
            if (result == null || !isEdgeSuccess(result)) return null;

            Object edgeOptional = createEdgeResultEdge.invoke(result);
            return Optional.class.getMethod("orElse", Object.class).invoke(edgeOptional, (Object) null);
        } catch (Throwable t) {
            logWireApiFailure(t);
            return null;
        }
    }

    /**
     * 在 headspan 的 lower_tension_wire 上给指定 dropper 安装 registration arm，
     * 之后 CATENARY_WIRE 才能以该 dropper 为连接点。
     */
    public static boolean addHeadspanRegistrationArm(
            Object headspanEdge, UUID dropperId, double fraction, ResourceLocation cantileverItemId
    ) {
        if (headspanEdge == null || dropperId == null || cantileverItemId == null || !ensureHeadspanApi()) {
            return false;
        }
        try {
            ItemStack stack = CommonUtil.getItemStack(cantileverItemId);
            if (stack.isEmpty()) return false;

            Object decoration = registrationArmDecorationCtor.newInstance(
                    stack, false, registrationArmNormalCentered, dropperId
            );
            int length = ((Number) wireEdgeLength.invoke(headspanEdge)).intValue();
            double pos = Math.max(0.5, Math.min(Math.max(0.5, length - 0.5), fraction * length));
            wireEdgeAddDecoration.invoke(headspanEdge, pos, "lower_tension_wire", decoration);
            // 旧 headspan 上已经有该 dropper 的 registration arm 时，
            // addDecoration 会返回 false；这仍然表示可以继续复用该连接点。
            return true;
        } catch (Throwable t) {
            logWireApiFailure(t);
            return false;
        }
    }

    /**
     * 为 headspan dropper 创建 catenary 连接节点。
     */
    public static Object createHeadspanCatenaryNode(Object headspanEdge, UUID dropperId) {
        if (headspanEdge == null || dropperId == null || !ensureHeadspanApi()) {
            return null;
        }
        try {
            UUID edgeId = (UUID) wireEdgeGetId.invoke(headspanEdge);
            Object wireId = wireIdCtor.newInstance(
                    edgeId, "dropper_l_" + dropperId, headspanWireType
            );
            return headspanConnectionNodeCtor.newInstance(wireId);
        } catch (Throwable t) {
            logWireApiFailure(t);
            return null;
        }
    }

    /**
     * 在两个 headspan dropper 节点之间创建 CATENARY_WIRE edge。
     */
    public static boolean connectHeadspanCatenary(ServerLevel world, Object nodeA, Object nodeB) {
        if (world == null || nodeA == null || nodeB == null || !ensureHeadspanApi()) {
            return false;
        }
        try {
            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return false;

            Object customData = customDataCtor.newInstance(new CompoundTag());
            Object pointIndex = mutableIntCtor.newInstance(0);
            Object result = wireGraphCreateEdge.invoke(
                    graph, catenaryWireType, customData, nodeA, nodeB, pointIndex, true
            );
            return result != null && isEdgeSuccess(result);
        } catch (Throwable t) {
            logWireApiFailure(t);
            return false;
        }
    }

    private static boolean isEdgeSuccess(Object result) throws Exception {
        return createEdgeResultSuccess == null
                || Boolean.TRUE.equals(createEdgeResultSuccess.invoke(result));
    }

    private static volatile Boolean wireApiAvailable;
    private static Method wireGraphManagerGet;
    private static Method wireGraphCreateEdge;
    private static Method createEdgeResultSuccess;
    private static Constructor<?> customDataCtor;
    private static Constructor<?> blockConnectorNodeCtor;
    private static Constructor<?> mutableIntCtor;
    private static Object catenaryGraphId;
    private static Object catenaryWireType;
    private static boolean wireApiFailureLogged;

    private static synchronized boolean ensureWireApi() {
        if (wireApiAvailable != null) return wireApiAvailable;
        try {
            Class<?> wiresApi = Class.forName("de.mrjulsen.wires.WiresApi");
            catenaryGraphId = wiresApi.getField("PAW_CATENARY_WIRES").get(null);

            Class<?> modWireRegistry = Class.forName("de.mrjulsen.paw.registry.ModWireRegistry");
            catenaryWireType = modWireRegistry.getField("CATENARY_WIRE").get(null);

            Class<?> graphIdClass = Class.forName("de.mrjulsen.wires.util.GraphId");
            Class<?> graphManagerClass = Class.forName("de.mrjulsen.wires.graph.WireGraphManager");
            wireGraphManagerGet = graphManagerClass.getMethod("get", Level.class, graphIdClass);

            Class<?> wireGraphClass = Class.forName("de.mrjulsen.wires.graph.WireGraph");
            for (Method method : wireGraphClass.getMethods()) {
                if (method.getName().equals("createEdge") && method.getParameterCount() == 6) {
                    wireGraphCreateEdge = method;
                    break;
                }
            }
            if (wireGraphCreateEdge == null) {
                throw new NoSuchMethodException("WireGraph.createEdge");
            }

            customDataCtor = Class.forName("de.mrjulsen.wires.item.CustomData")
                    .getConstructor(CompoundTag.class);
            blockConnectorNodeCtor = Class.forName("de.mrjulsen.wires.graph.data.node.BlockConnectorNodeData")
                    .getConstructor(BlockPos.class);
            mutableIntCtor = Class.forName("org.apache.commons.lang3.mutable.MutableInt")
                    .getConstructor(int.class);

            try {
                Class<?> resultClass = Class.forName("de.mrjulsen.wires.graph.WireGraph$CreateEdgeResult");
                createEdgeResultSuccess = resultClass.getMethod("success");
            } catch (Throwable ignored) {
                createEdgeResultSuccess = null;
            }

            wireApiAvailable = true;
        } catch (Throwable t) {
            wireApiAvailable = false;
            logWireApiFailure(t);
        }
        return wireApiAvailable;
    }

    private static void logWireApiFailure(Throwable t) {
        if (wireApiFailureLogged) return;
        wireApiFailureLogged = true;
        System.out.println("[StationBuilder] CPW wire graph API unavailable: " + t);
    }

    private static volatile Boolean headspanApiAvailable;
    private static Object headspanWireType;
    private static Constructor<?> latticeMastNodeCtor;
    private static Constructor<?> registrationArmDecorationCtor;
    private static Object registrationArmNormalCentered;
    private static Constructor<?> headspanConnectionNodeCtor;
    private static Constructor<?> wireIdCtor;
    private static Method wireEdgeGetId;
    private static Method wireEdgeLength;
    private static Method wireEdgeAddDecoration;
    private static Method createEdgeResultEdge;
    private static Constructor<?> wireEdgeHashCtor;
    private static Method wireGraphGetEdgeByHash;

    /**
     * headspan 相关反射单独初始化，失败时不影响已经可用的普通 catenary 连线。
     */
    private static synchronized boolean ensureHeadspanApi() {
        if (headspanApiAvailable != null) return headspanApiAvailable;
        if (!ensureWireApi()) {
            headspanApiAvailable = false;
            return false;
        }
        try {
            Class<?> modWireRegistry = Class.forName("de.mrjulsen.paw.registry.ModWireRegistry");
            headspanWireType = modWireRegistry.getField("CATENARY_HEADSPAN").get(null);

            latticeMastNodeCtor = Class.forName("de.mrjulsen.wires.graph.data.node.LatticeMastNodeData")
                    .getConstructor(BlockPos.class);

            Class<?> wireEdgeClass = Class.forName("de.mrjulsen.wires.graph.WireEdge");
            wireEdgeGetId = wireEdgeClass.getMethod("getId");
            wireEdgeLength = wireEdgeClass.getMethod("length");
            wireEdgeAddDecoration = wireEdgeClass.getMethod(
                    "addDecoration", double.class, String.class,
                    Class.forName("de.mrjulsen.wires.decoration.IWireDecoration")
            );

            Class<?> resultClass = Class.forName("de.mrjulsen.wires.graph.WireGraph$CreateEdgeResult");
            createEdgeResultEdge = resultClass.getMethod("edge");

            Class<?> customDataClass = Class.forName("de.mrjulsen.wires.item.CustomData");
            Class<?> nodeDataClass = Class.forName("de.mrjulsen.wires.graph.data.node.NodeData");
            Class<?> wireEdgeHashClass = Class.forName("de.mrjulsen.wires.graph.data.WireEdgeHash");
            wireEdgeHashCtor = wireEdgeHashClass.getConstructor(customDataClass, nodeDataClass, nodeDataClass);
            wireGraphGetEdgeByHash = Class.forName("de.mrjulsen.wires.graph.WireGraph")
                    .getMethod("getEdge", wireEdgeHashClass);

            Class<?> registrationStateClass = Class.forName("de.mrjulsen.paw.block.RegistrationArmBlock$State");
            registrationArmNormalCentered = registrationStateClass.getField("NORMAL_CENTERED").get(null);
            registrationArmDecorationCtor = Class.forName("de.mrjulsen.paw.registry.RegistrationArmWireDecoration")
                    .getConstructor(ItemStack.class, boolean.class, registrationStateClass, UUID.class);

            Class<?> wireIdClass = Class.forName("de.mrjulsen.wires.network.WireId");
            wireIdCtor = wireIdClass.getConstructor(
                    UUID.class, String.class, Class.forName("de.mrjulsen.wires.IWireType")
            );
            headspanConnectionNodeCtor = Class.forName(
                            "de.mrjulsen.wires.graph.data.node.CatenaryHeadspanConnectionNodeData")
                    .getConstructor(wireIdClass);

            headspanApiAvailable = true;
        } catch (Throwable t) {
            headspanApiAvailable = false;
            logWireApiFailure(t);
        }
        return headspanApiAvailable;
    }

    /**
     * 判断方块 ID 是否可作为 CPW 支柱（mast）。
     * CPW 本体和常见附属模组的 mast 方块路径都包含 mast。
     */
    public static boolean isMastBlockId(ResourceLocation id) {
        return id != null && id.getPath().contains("mast");
    }

    public static boolean isCantileverBlockId(ResourceLocation id) {
        return id != null && id.getPath().contains("cantilever");
    }

    /**
     * 站台接触网只接受 mast 方块；配置为墙、栅栏等非 mast 方块时回退到默认格构柱。
     */
    public static ResourceLocation resolveMastId(ResourceLocation configured) {
        return isMastBlockId(configured) ? configured : DEFAULT_MAST;
    }

    public static boolean isMastBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        return isMastBlockId(CommonUtil.getBlockId(state.getBlock()));
    }

    /**
     * 在指定竖直范围内查找已有的 mast。
     * 轨道间隙模式用它判断相邻股道是否已经建好 mast，从而直接重复利用。
     */
    public static boolean hasMastColumn(Level world, int x, int z, int minY, int maxY) {
        if (minY > maxY) return false;
        for (int y = minY; y <= maxY; y++) {
            if (isMastBlock(world, new BlockPos(x, y, z))) return true;
        }
        return false;
    }

    public static boolean isCPWBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        ResourceLocation id = CommonUtil.getBlockId(state.getBlock());
        return id != null && id.getNamespace().equals("pantographsandwires");
    }
}