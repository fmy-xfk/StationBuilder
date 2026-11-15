package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.blocks.ModBlocks;
import cn.myfrank.stationbuilder.blocks.StationBuilderBlockEntity;
import cn.myfrank.stationbuilder.elements.PassageConfig;
import cn.myfrank.stationbuilder.elements.StationElement;
import cn.myfrank.stationbuilder.generator.StationGenerator;
import cn.myfrank.stationbuilder.items.*;
import cn.myfrank.stationbuilder.manager.BuildingTemplateManager;
import cn.myfrank.stationbuilder.manager.PlacerHistoryManager;
import cn.myfrank.stationbuilder.mtr.MtrDepotOffsetWriter;
import cn.myfrank.stationbuilder.mtr.MtrGameDataSource;
import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.scheduler.PayloadCompression;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(StationBuilder.MOD_ID)
public final class StationBuilder {
    public static final String MOD_ID = "stationbuilder";
    public static final int DEFAULT_STATION_LENGTH = 51;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceLocation PACKET_SYNC_OPEN = ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open");
    public static final ResourceLocation PACKET_SAVE_DATA = ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_data");
    public static final ResourceLocation PACKET_BUILD = ResourceLocation.fromNamespaceAndPath(MOD_ID, "build_station");
    public static final ResourceLocation PACKET_SYNC_OPEN_RAIL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_rail");
    public static final ResourceLocation PACKET_SAVE_RAIL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_data_rail");
    public static final ResourceLocation PACKET_CLEAR_RAIL = ResourceLocation.fromNamespaceAndPath(MOD_ID, "clear_rail_state");
    public static final ResourceLocation PACKET_SYNC_OPEN_SELECTOR = ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_selector");
    public static final ResourceLocation PACKET_SAVE_SELECTION = ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_selection");
    public static final ResourceLocation PACKET_SYNC_OPEN_PLACER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "sync_open_placer");
    public static final ResourceLocation PACKET_SAVE_PLACER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "save_placer");
    public static final ResourceLocation PACKET_UNDO_PLACER = ResourceLocation.fromNamespaceAndPath(MOD_ID, "undo_placer");
    public static final ResourceLocation PACKET_MTR_SCHEDULER_REQUEST = ResourceLocation.fromNamespaceAndPath(MOD_ID, "mtr_scheduler_request");
    public static final ResourceLocation PACKET_MTR_SCHEDULER_WRITE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "mtr_scheduler_write");
    public static final ResourceLocation PACKET_MTR_SCHEDULER_DATA = ResourceLocation.fromNamespaceAndPath(MOD_ID, "mtr_scheduler_data");

    public StationBuilder() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        PlatformServices.install(new ForgePlatformServices());
        StationBuilder.initCommon(modEventBus);
        ForgeRegistry.register(modEventBus);
    }

    public static FriendlyByteBuf buf(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        return buf;
    }

    public static void initCommon(IEventBus bus) {
        ModBlocks.register(bus);
        ModItems.register(bus);
        BuildingTemplateManager.loadTemplates();
        registerNetworking();
    }

    private static void registerNetworking() {
        PlatformServices.registerServerReceiver(PACKET_SAVE_DATA, StationBuilder::handleSaveStation);
        PlatformServices.registerServerReceiver(PACKET_BUILD, StationBuilder::handleBuildStation);
        PlatformServices.registerServerReceiver(PACKET_SAVE_RAIL, StationBuilder::handleSaveRail);
        PlatformServices.registerServerReceiver(PACKET_CLEAR_RAIL, StationBuilder::handleClearRail);
        PlatformServices.registerServerReceiver(PACKET_SAVE_SELECTION, StationBuilder::handleSaveSelection);
        PlatformServices.registerServerReceiver(PACKET_SAVE_PLACER, StationBuilder::handleSavePlacer);
        PlatformServices.registerServerReceiver(PACKET_UNDO_PLACER, StationBuilder::handleUndoPlacer);
        PlatformServices.registerServerReceiver(PACKET_MTR_SCHEDULER_REQUEST, StationBuilder::handleMtrSchedulerRequest);
        PlatformServices.registerServerReceiver(PACKET_MTR_SCHEDULER_WRITE, StationBuilder::handleMtrSchedulerWrite);
    }

    private static void handleMtrSchedulerRequest(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        int token = buf.readInt();
        if (player.getServer() == null) {
            return;
        }
        player.getServer().execute(() -> {
            if (!PlatformServices.isModLoaded("mtr")) {
                sendSchedulerError(player, token, "MTR is not installed on this server");
                return;
            }
            try {
                MtrGameDataSource.collect(player.getServer(), (net.minecraft.server.level.ServerLevel) player.level(),
                        snapshot -> sendSchedulerSnapshot(player, token, snapshot));
            } catch (Throwable throwable) {
                sendSchedulerSnapshot(player, token,
                        MtrGameDataSource.Snapshot.error("Could not request MTR data: " + throwable.getMessage()));
            }
        });
    }

    private static void handleMtrSchedulerWrite(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        int token = buf.readInt();
        String offsetsJson = buf.readUtf();
        if (player.getServer() == null) {
            return;
        }
        player.getServer().execute(() -> {
            if (!PlatformServices.isModLoaded("mtr")) {
                sendSchedulerError(player, token, "MTR is not installed on this server");
                return;
            }
            Map<String, Long> offsets;
            try {
                offsets = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : Json.object(Json.parse(offsetsJson)).entrySet()) {
                    offsets.put(entry.getKey(), Json.longValue(entry.getValue(), 0L));
                }
            } catch (RuntimeException exception) {
                sendSchedulerSnapshot(player, token,
                        MtrGameDataSource.Snapshot.error("Invalid depot offsets: " + exception.getMessage()));
                return;
            }
            try {
                MtrGameDataSource.collect(player.getServer(),
                        (net.minecraft.server.level.ServerLevel) player.level(), snapshot -> {
                            if (!snapshot.isSuccess()) {
                                sendSchedulerSnapshot(player, token, snapshot);
                                return;
                            }
                            MtrDepotOffsetWriter.Result result =
                                    MtrDepotOffsetWriter.write(snapshot.response(), offsets);
                            if (!result.success()) {
                                sendSchedulerSnapshot(player, token,
                                        MtrGameDataSource.Snapshot.error(result.message()));
                                return;
                            }
                            MtrGameDataSource.collect(player.getServer(),
                                    (net.minecraft.server.level.ServerLevel) player.level(),
                                    fresh -> sendSchedulerSnapshot(player, token, fresh));
                        });
            } catch (Throwable throwable) {
                sendSchedulerSnapshot(player, token,
                        MtrGameDataSource.Snapshot.error("Could not write MTR data: " + throwable.getMessage()));
            }
        });
    }

    private static void sendSchedulerError(net.minecraft.server.level.ServerPlayer player, int token, String message) {
        PlatformServices.sendToPlayer(player, PACKET_MTR_SCHEDULER_DATA, buf(buffer -> {
            buffer.writeInt(token);
            buffer.writeInt(0);
            buffer.writeInt(1);
            buffer.writeBoolean(false);
            buffer.writeUtf(message);
            buffer.writeByteArray(new byte[0]);
        }));
    }

    private static void sendSchedulerSnapshot(
            net.minecraft.server.level.ServerPlayer player,
            int token,
            MtrGameDataSource.Snapshot snapshot
    ) {
        if (!snapshot.isSuccess()) {
            PlatformServices.sendToPlayer(player, PACKET_MTR_SCHEDULER_DATA, buf(buffer -> {
                buffer.writeInt(token);
                buffer.writeInt(0);
                buffer.writeInt(1);
                buffer.writeBoolean(false);
                buffer.writeUtf(snapshot.error());
                buffer.writeByteArray(new byte[0]);
            }));
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("stations", snapshot.stationsAndRoutesJson());
        envelope.put("departures", snapshot.departuresJson());
        byte[] compressed = PayloadCompression.compress(Json.writeCompact(envelope));
        int chunkSize = 30_000;
        int parts = Math.max(1, (compressed.length + chunkSize - 1) / chunkSize);
        for (int part = 0; part < parts; part++) {
            final int from = part * chunkSize;
            final int to = Math.min(compressed.length, from + chunkSize);
            final int currentPart = part;
            PlatformServices.sendToPlayer(player, PACKET_MTR_SCHEDULER_DATA, buf(buffer -> {
                buffer.writeInt(token);
                buffer.writeInt(currentPart);
                buffer.writeInt(parts);
                buffer.writeBoolean(true);
                buffer.writeUtf("");
                buffer.writeByteArray(java.util.Arrays.copyOfRange(compressed, from, to));
            }));
        }
    }

    private static void handleSaveStation(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        buf.readInt();
        int length = buf.readInt();
        PassageConfig passage = PassageConfig.read(buf);
        int count = buf.readInt();
        List<StationElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) elements.add(StationElement.read(buf));
        if (player.getServer() != null) player.getServer().execute(() -> {
            if (player.level().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
                be.length = length; be.passage = passage; be.elements = elements; be.setChanged();
            }
        });
    }

    private static void handleBuildStation(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int facingInt = buf.readInt();
        int length = buf.readInt();
        PassageConfig passage = PassageConfig.read(buf);
        int count = buf.readInt();
        List<StationElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) elements.add(StationElement.read(buf));
        if (player.getServer() != null) player.getServer().execute(() -> {
            if (player.level().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
                be.length = length; be.passage = passage; be.elements = elements; be.setChanged();
            }
            StationGenerator.build(player, player.serverLevel(), pos, Direction.from2DDataValue(facingInt), length, passage, elements);
        });
    }

    private static void handleSaveRail(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        if (player.getServer() != null) player.getServer().execute(() -> {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof RailBuilderItem && nbt != null) {
                RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
                cfg.fromNbt(nbt); cfg.saveToItem(stack);
            }
        });
    }

    private static void handleClearRail(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        if (player.getServer() != null) player.getServer().execute(() -> {
            ItemStack stack = player.getMainHandItem();
            if (!stack.isEmpty()) {
                RailBuilderState.clear(stack);
                if (stack.hasTag()) stack.getTag().remove("CustomModelData");
                player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.end"), true);
            }
        });
    }

    private static void handleSaveSelection(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        String name = buf.readUtf();
        BlockPos p1 = buf.readBlockPos();
        BlockPos p2 = buf.readBlockPos();
        boolean includeEntities = buf.readBoolean();
        if (player.getServer() != null) player.getServer().execute(() -> {
            net.minecraft.server.level.ServerLevel world = player.serverLevel();
            BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
            BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
            net.minecraft.core.Vec3i size = new net.minecraft.core.Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);

            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(world, min, size, includeEntities, null);
            BuildingTemplateManager.addTemplate(name, template);
            player.sendSystemMessage(Component.translatable("message.stationbuilder.save_selection_success", name).withStyle(net.minecraft.ChatFormatting.GREEN));
        });
    }

    private static void handleSavePlacer(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        if (player.getServer() != null) player.getServer().execute(() -> {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof BuildingPlacerItem && nbt != null) {
                BuildingPlacerConfig cfg = BuildingPlacerConfig.fromItem(stack);
                cfg.fromNbt(nbt);
                cfg.saveToItem(stack);
            }
        });
    }

    private static void handleUndoPlacer(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        if (player.getServer() != null) player.getServer().execute(() -> {
            // Global undo history: the latest action may come from any tool.
            String tool = PlacerHistoryManager.undo(player);
            if (tool != null) {
                String key = switch (tool) {
                    case "station" -> "message.stationbuilder.undo_station";
                    case "rail" -> "message.stationbuilder.undo_rail";
                    case "placer" -> "message.stationbuilder.undo_placer";
                    default -> "message.stationbuilder.undo_success";
                };
                player.sendSystemMessage(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GREEN), true);
            } else {
                player.sendSystemMessage(Component.translatable("message.stationbuilder.undo_no_history").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
        });
    }

    public static String getRotName(net.minecraft.world.level.block.Rotation rotation) {
        return Component.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
    }
}
