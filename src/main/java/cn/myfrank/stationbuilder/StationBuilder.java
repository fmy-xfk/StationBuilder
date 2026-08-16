package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import io.netty.buffer.Unpooled;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
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

    public static boolean isMtrLoaded() { return PlatformServices.isModLoaded("mtr"); }
    public static boolean isMsdLoaded() { return PlatformServices.isModLoaded("msd"); }

    public static boolean isSoftTransparent(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }
    public static boolean isNotLiquidTransparent(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
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
    }

    private static void handleSaveStation(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        buf.readInt();
        int length = buf.readInt();
        int count = buf.readInt();
        List<StationElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) elements.add(StationElement.read(buf));
        if (player.getServer() != null) player.getServer().execute(() -> {
            if (player.level().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
                be.length = length; be.elements = elements; be.setChanged();
            }
        });
    }

    private static void handleBuildStation(net.minecraft.server.level.ServerPlayer player, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int facingInt = buf.readInt();
        int length = buf.readInt();
        int count = buf.readInt();
        List<StationElement> elements = new ArrayList<>();
        for (int i = 0; i < count; i++) elements.add(StationElement.read(buf));
        if (player.getServer() != null) player.getServer().execute(() -> {
            if (player.level().getBlockEntity(pos) instanceof StationBuilderBlockEntity be) {
                be.length = length; be.elements = elements; be.setChanged();
            }
            StationGenerator.build(player, player.serverLevel(), pos, Direction.from2DDataValue(facingInt), length, elements);
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
}