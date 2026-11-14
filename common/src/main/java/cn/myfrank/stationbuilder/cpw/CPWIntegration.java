package cn.myfrank.stationbuilder.cpw;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.TickScheduler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CPWIntegration {
    public static final Identifier WIRE_COIL = Identifier.of("pantographsandwires", "wire_coil");
    public static final Identifier DEFAULT_MAST = Identifier.of("pantographsandwires", "lattice_mast");
    public static final Identifier DEFAULT_CANTILEVER = Identifier.of("pantographsandwires", "cantilever_brown");

    private static final TagKey<Block> HEADSPAN_CONNECTABLE = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier.of("pantographsandwires", "catenary_headspan_connectable")
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

    public static CPWAngle getCPWAngles(Vec3d dir) {
        return CPWAngle.fromVector(dir);
    }

    public static BlockState applyMastProperties(BlockState state, CPWAngle angle, String part) {
        return applyMastProperties(state, angle.getFacing(), angle.getRotation(), part);
    }

    public static BlockState applyMastProperties(BlockState state, Direction facing, int rotation, String part) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("facing") && prop.getType() == Direction.class) {
                @SuppressWarnings("unchecked")
                Property<Direction> dirProp = (Property<Direction>) prop;
                if (dirProp.getValues().contains(facing)) {
                    state = state.with(dirProp, facing);
                }
            } else if (prop.getName().equals("rotation") && prop.getType() == Integer.class) {
                @SuppressWarnings("unchecked")
                Property<Integer> intProp = (Property<Integer>) prop;
                if (intProp.getValues().contains(rotation)) {
                    state = state.with(intProp, rotation);
                }
            } else if (part != null && prop.getName().equals("part")) {
                for (Comparable<?> val : prop.getValues()) {
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
        return state.with((Property<T>) prop, (T) val);
    }

    public static void placeMastBlock(ServerWorld world, BlockPos pos, CPWAngle angle, String part, Identifier mastId) {
        BlockState base = CommonUtil.getBlockState(mastId);
        BlockState configured = applyMastProperties(base, angle, part);
        world.setBlockState(pos, configured, Block.NOTIFY_ALL);
    }

    public static void buildMastColumn(ServerWorld world, int x, int z, int minY, int maxY, CPWAngle angle, Identifier mastId) {
        if (minY > maxY) return;
        for (int y = minY; y <= maxY; y++) {
            String part = "in_between";
            if (y == minY && minY != maxY) part = "bottom";
            else if (y == maxY) part = "top";
            placeMastBlock(world, new BlockPos(x, y, z), angle, part, mastId);
        }
    }

    public static boolean placeCantilever(ServerWorld world, BlockPos cantileverPos, CPWAngle angle, Identifier cantileverId) {
        BlockState base = CommonUtil.getBlockState(cantileverId);
        BlockState configured = applyMastProperties(base, angle, null);
        world.setBlockState(cantileverPos, configured, Block.NOTIFY_ALL);
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
    public static void connectWire(ServerWorld world, PlayerEntity player, BlockPos from, BlockPos to, Identifier wireCoilId) {
        if (world == null || from == null || to == null || from.equals(to)) return;
        // 延后 1 tick，确保 cantilever 的 BlockEntity 已创建，WireGraph 能读取连接点。
        TickScheduler.schedule(1, () -> connectCatenaryWire(world, from, to));
    }

    public static boolean connectCatenaryWire(ServerWorld world, BlockPos fromCantilever, BlockPos toCantilever) {
        if (!ensureWireApi()) return false;
        try {
            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return false;

            Object customData = customDataCtor.newInstance(new NbtCompound());
            Object nodeA = blockConnectorNodeCtor.newInstance(fromCantilever);
            Object nodeB = blockConnectorNodeCtor.newInstance(toCantilever);
            Object pointIndex = mutableIntCtor.newInstance(0);
            Object result = wireGraphCreateEdge.invoke(
                    graph, catenaryWireType, customData, nodeA, nodeB, pointIndex, true
            );
            if (result == null) return false;
            return isEdgeSuccess(result);
        } catch (Throwable t) {
            logWireApiFailure(t);
            return false;
        }
    }

    // ========================================================
    // Headspan（多股道并行）
    // ========================================================

    public static Identifier resolveHeadspanMastId(Identifier configured) {
        if (configured != null && CommonUtil.getBlockState(configured).isIn(HEADSPAN_CONNECTABLE)) {
            return configured;
        }
        return DEFAULT_MAST;
    }

    public static Object createHeadspanEdge(
            ServerWorld world, BlockPos mastA, BlockPos mastB,
            float upperWireHeight, float topWireHeight, List<HeadspanDropper> droppers
    ) {
        if (world == null || mastA == null || mastB == null || droppers == null || !ensureHeadspanApi()) {
            return null;
        }
        try {
            NbtCompound common = new NbtCompound();
            common.putFloat("UpperWireHeight", upperWireHeight);
            common.putFloat("TopWireHeight", topWireHeight);

            NbtList dropperList = new NbtList();
            for (HeadspanDropper dropper : droppers) {
                NbtCompound tag = new NbtCompound();
                tag.putUuid("Id", dropper.id);
                tag.putDouble("Pos", dropper.fraction);
                dropperList.add(tag);
            }
            common.put("Droppers", dropperList);

            NbtCompound edgeData = new NbtCompound();
            edgeData.put("CustomData", common);
            edgeData.putInt("PointsCount", 2);

            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return null;

            Object customData = customDataCtor.newInstance(edgeData);
            Object nodeA = latticeMastNodeCtor.newInstance(mastA);
            Object nodeB = latticeMastNodeCtor.newInstance(mastB);
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

    public static boolean addHeadspanRegistrationArm(
            Object headspanEdge, UUID dropperId, double fraction, Identifier cantileverItemId
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
            return Boolean.TRUE.equals(
                    wireEdgeAddDecoration.invoke(headspanEdge, pos, "lower_tension_wire", decoration)
            );
        } catch (Throwable t) {
            logWireApiFailure(t);
            return false;
        }
    }

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

    public static boolean connectHeadspanCatenary(ServerWorld world, Object nodeA, Object nodeB) {
        if (world == null || nodeA == null || nodeB == null || !ensureHeadspanApi()) {
            return false;
        }
        try {
            Object graph = wireGraphManagerGet.invoke(null, world, catenaryGraphId);
            if (graph == null) return false;

            Object customData = customDataCtor.newInstance(new NbtCompound());
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

    public static Identifier resolveMastId(Identifier configured) {
        return isMastBlockId(configured) ? configured : DEFAULT_MAST;
    }

    public static Identifier resolveCantileverId(Identifier configured) {
        return configured != null && configured.getPath().contains("cantilever") ? configured : DEFAULT_CANTILEVER;
    }

    public static boolean isMastBlockId(Identifier id) {
        return id != null && id.getPath().contains("mast");
    }

    public static boolean isMastBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        return isMastBlockId(Registries.BLOCK.getId(state.getBlock()));
    }

    public static boolean hasMastColumn(World world, int x, int z, int minY, int maxY) {
        if (minY > maxY) return false;
        for (int y = minY; y <= maxY; y++) {
            if (isMastBlock(world, new BlockPos(x, y, z))) return true;
        }
        return false;
    }

    public static boolean isCPWBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return id != null && id.getNamespace().equals("pantographsandwires");
    }

    // ========================================================
    // 反射：CPW WireGraph / headspan API
    // ========================================================

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
            wireGraphManagerGet = graphManagerClass.getMethod("get", World.class, graphIdClass);

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
                    .getConstructor(NbtCompound.class);
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

    private static void logWireApiFailure(Throwable t) {
        if (wireApiFailureLogged) return;
        wireApiFailureLogged = true;
        System.out.println("[StationBuilder] CPW wire graph API unavailable: " + t);
    }
}
