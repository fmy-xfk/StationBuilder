package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(StationBuilder.MOD_ID)
public class StationBuilder {
    public static final String MOD_ID = "stationbuilder";
    public static final int DEFAULT_STATION_LENGTH = 51;
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STATION_TAB =
            CREATIVE_MODE_TABS.register("station_group", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stationbuilder.group"))
                    .icon(() -> new ItemStack(ModBlocks.STATION_BUILDER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.STATION_BUILDER_ITEM.get());
                        output.accept(ModItems.RAIL_BUILDER_ITEM.get());
                        output.accept(ModItems.BUILDING_SELECTOR_ITEM.get());
                        output.accept(ModItems.BUILDING_PLACER_ITEM.get());
                    })
                    .build());

    // ============ Payloads ============

    public static final StreamCodec<FriendlyByteBuf, List<StationElement>> ELEMENTS_CODEC =
            StreamCodec.of(
                    (buf, elements) -> {
                        buf.writeInt(elements.size());
                        for (StationElement element : elements) {
                            element.write(buf);
                        }
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<StationElement> elements = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            elements.add(StationElement.read(buf));
                        }
                        return elements;
                    }
            );

    public record SyncOpenStationPayload(BlockPos pos, int facing, CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SyncOpenStationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open"));
        public static final StreamCodec<FriendlyByteBuf, SyncOpenStationPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SyncOpenStationPayload::pos,
                        ByteBufCodecs.VAR_INT, SyncOpenStationPayload::facing,
                        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SyncOpenStationPayload::nbt,
                        SyncOpenStationPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveStationPayload(BlockPos pos, int facing, int length, List<StationElement> elements) implements CustomPacketPayload {
        public static final Type<SaveStationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_data"));
        public static final StreamCodec<FriendlyByteBuf, SaveStationPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SaveStationPayload::pos,
                        ByteBufCodecs.VAR_INT, SaveStationPayload::facing,
                        ByteBufCodecs.VAR_INT, SaveStationPayload::length,
                        ELEMENTS_CODEC, SaveStationPayload::elements,
                        SaveStationPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BuildStationPayload(BlockPos pos, int facing, int length, List<StationElement> elements) implements CustomPacketPayload {
        public static final Type<BuildStationPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "build_station"));
        public static final StreamCodec<FriendlyByteBuf, BuildStationPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, BuildStationPayload::pos,
                        ByteBufCodecs.VAR_INT, BuildStationPayload::facing,
                        ByteBufCodecs.VAR_INT, BuildStationPayload::length,
                        ELEMENTS_CODEC, BuildStationPayload::elements,
                        BuildStationPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncOpenRailPayload(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SyncOpenRailPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_rail"));
        public static final StreamCodec<FriendlyByteBuf, SyncOpenRailPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SyncOpenRailPayload::nbt,
                        SyncOpenRailPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveRailPayload(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SaveRailPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_data_rail"));
        public static final StreamCodec<FriendlyByteBuf, SaveRailPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SaveRailPayload::nbt,
                        SaveRailPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClearRailStatePayload() implements CustomPacketPayload {
        public static final Type<ClearRailStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "clear_rail_state"));
        public static final StreamCodec<FriendlyByteBuf, ClearRailStatePayload> STREAM_CODEC =
                StreamCodec.unit(new ClearRailStatePayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncOpenSelectorPayload(BlockPos pos1, BlockPos pos2) implements CustomPacketPayload {
        public static final Type<SyncOpenSelectorPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_selector"));
        public static final StreamCodec<FriendlyByteBuf, SyncOpenSelectorPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SyncOpenSelectorPayload::pos1,
                        BlockPos.STREAM_CODEC, SyncOpenSelectorPayload::pos2,
                        SyncOpenSelectorPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveSelectionPayload(String name, BlockPos pos1, BlockPos pos2, boolean includeEntities) implements CustomPacketPayload {
        public static final Type<SaveSelectionPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_selection"));
        public static final StreamCodec<FriendlyByteBuf, SaveSelectionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SaveSelectionPayload::name,
                        BlockPos.STREAM_CODEC, SaveSelectionPayload::pos1,
                        BlockPos.STREAM_CODEC, SaveSelectionPayload::pos2,
                        ByteBufCodecs.BOOL, SaveSelectionPayload::includeEntities,
                        SaveSelectionPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncOpenPlacerPayload(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SyncOpenPlacerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_placer"));
        public static final StreamCodec<FriendlyByteBuf, SyncOpenPlacerPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SyncOpenPlacerPayload::nbt,
                        SyncOpenPlacerPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SavePlacerPayload(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SavePlacerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_placer"));
        public static final StreamCodec<FriendlyByteBuf, SavePlacerPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.TRUSTED_COMPOUND_TAG, SavePlacerPayload::nbt,
                        SavePlacerPayload::new
                );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UndoPlacerPayload() implements CustomPacketPayload {
        public static final Type<UndoPlacerPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "undo_placer"));
        public static final StreamCodec<FriendlyByteBuf, UndoPlacerPayload> STREAM_CODEC =
                StreamCodec.unit(new UndoPlacerPayload());
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ============ Mod loaded checks ============

    public static boolean isMtrLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("mtr");
    }

    public static boolean isMsdLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("msd");
    }

    public static boolean isSoftTransparent(BlockState state) {
        return state.isAir() || state.canBeReplaced()
                || !state.getFluidState().isEmpty()
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES);
    }

    public static boolean isNotLiquidTransparent(BlockState state) {
        return state.isAir() || state.canBeReplaced() ||
                state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    public static String getRotName(Rotation rotation) {
        return Component.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
    }

    public StationBuilder(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModComponents.DATA_COMPONENTS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(StationBuilder::registerPayloads);
        modEventBus.addListener(StationBuilder::commonSetup);

        NeoForge.EVENT_BUS.register(StationBuilder.class);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        TickScheduler.init();
        // 资源在服务端启动时按需加载，见 ServerStartingEvent
        if (FMLEnvironment.dist.isClient()) {
            // 客户端无需在 commonSetup 预加载
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);

        // C2S
        registrar.playToServer(UndoPlacerPayload.TYPE, UndoPlacerPayload.STREAM_CODEC, StationBuilder::handleUndoPlacer);
        registrar.playToServer(SaveStationPayload.TYPE, SaveStationPayload.STREAM_CODEC, StationBuilder::handleSaveStation);
        registrar.playToServer(BuildStationPayload.TYPE, BuildStationPayload.STREAM_CODEC, StationBuilder::handleBuildStation);
        registrar.playToServer(SaveRailPayload.TYPE, SaveRailPayload.STREAM_CODEC, StationBuilder::handleSaveRail);
        registrar.playToServer(ClearRailStatePayload.TYPE, ClearRailStatePayload.STREAM_CODEC, StationBuilder::handleClearRailState);
        registrar.playToServer(SavePlacerPayload.TYPE, SavePlacerPayload.STREAM_CODEC, StationBuilder::handleSavePlacer);
        registrar.playToServer(SaveSelectionPayload.TYPE, SaveSelectionPayload.STREAM_CODEC, StationBuilder::handleSaveSelection);
    }

    // ============ C2S handlers ============

    private static void handleUndoPlacer(UndoPlacerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player.getMainHandItem().getItem() instanceof BuildingPlacerItem) {
                boolean success = PlacerHistoryManager.undo(player);
                if (success) {
                    player.displayClientMessage(Component.translatable("message.stationbuilder.undo_success")
                            .withStyle(ChatFormatting.GREEN), false);
                } else {
                    player.displayClientMessage(Component.translatable("message.stationbuilder.undo_no_history")
                            .withStyle(ChatFormatting.RED), false);
                }
            }
        });
    }

    private static void handleSaveStation(SaveStationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.pos()) instanceof StationBuilderBlockEntity be) {
                be.length = payload.length();
                be.elements = new ArrayList<>(payload.elements());
                be.setChanged();
            }
        });
    }

    private static void handleBuildStation(BuildStationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.pos()) instanceof StationBuilderBlockEntity be) {
                be.length = payload.length();
                be.elements = new ArrayList<>(payload.elements());
                be.setChanged();
            }
            StationGenerator.build(
                    (ServerPlayer) context.player(),
                    (ServerLevel) context.player().level(),
                    payload.pos(),
                    Direction.from2DDataValue(payload.facing()),
                    payload.length(),
                    new ArrayList<>(payload.elements())
            );
        });
    }

    private static void handleSaveRail(SaveRailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getMainHandItem();
            if (stack.getItem() instanceof RailBuilderItem) {
                RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
                cfg.fromNbt(payload.nbt());
                cfg.saveToItem(stack);
            }
        });
    }

    private static void handleClearRailState(ClearRailStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getMainHandItem();
            if (!stack.isEmpty()) {
                RailBuilderState.clear(stack);
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
                context.player().displayClientMessage(
                        Component.translatable("message.stationbuilder.rail_builder.end")
                , false);
            }
        });
    }

    private static void handleSavePlacer(SavePlacerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getMainHandItem();
            if (stack.getItem() instanceof BuildingPlacerItem) {
                BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
                cfg.fromNbt(payload.nbt());
                cfg.saveToItem(stack);
            }
        });
    }

    private static void handleSaveSelection(SaveSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerLevel world = (ServerLevel) context.player().level();
            BlockPos p1 = payload.pos1();
            BlockPos p2 = payload.pos2();
            if (p1 == null || p2 == null) return;

            BlockPos min = new BlockPos(
                    Math.min(p1.getX(), p2.getX()),
                    Math.min(p1.getY(), p2.getY()),
                    Math.min(p1.getZ(), p2.getZ())
            );
            BlockPos max = new BlockPos(
                    Math.max(p1.getX(), p2.getX()),
                    Math.max(p1.getY(), p2.getY()),
                    Math.max(p1.getZ(), p2.getZ())
            );
            net.minecraft.core.Vec3i size = new net.minecraft.core.Vec3i(
                    max.getX() - min.getX() + 1,
                    max.getY() - min.getY() + 1,
                    max.getZ() - min.getZ() + 1
            );

            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(world, min, size, payload.includeEntities(), null);
            BuildingTemplateManager.addTemplate(payload.name(), template);

            context.player().displayClientMessage(Component.translatable("message.stationbuilder.save_selection_success", payload.name())
                    .withStyle(ChatFormatting.GREEN), false);
        });
    }

    // ============ Events ============

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.getItem() instanceof BuildingSelectorItem) {
            BuildingSelectorItem.setPos1(stack, event.getPos());
            event.getEntity().displayClientMessage(
                    Component.translatable("message.stationbuilder.pos1_set_to:", event.getPos().toShortString())
                            .withStyle(ChatFormatting.GREEN),
                    false
            );
            event.setCanceled(true); // 拦截默认打破方块行为
        }
    }
}
