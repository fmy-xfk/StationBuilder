package cn.myfrank.stationbuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ForgePlatformServices implements PlatformServices.Impl {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(
                    ResourceLocation.fromNamespaceAndPath(StationBuilder.MOD_ID, "main"))
            .networkProtocolVersion(1)
            .clientAcceptedVersions((status, version) -> true)
            .serverAcceptedVersions((status, version) -> true)
            .simpleChannel();

    private static final Map<ResourceLocation, PlatformServices.ServerReceiver> SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PlatformServices.ClientReceiver> CLIENT = new ConcurrentHashMap<>();
    private static boolean initialized;

    static void initChannel() {
        if (initialized) return;
        initialized = true;

        StationBuilder.LOGGER.info("[Network] Initializing Network Channel...");

        CHANNEL.messageBuilder(ServerboundRawPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRawPacket::encode)
                .decoder(ServerboundRawPacket::decode)
                .consumerMainThread(ForgePlatformServices::handleServer)
                .add();

        CHANNEL.messageBuilder(ClientboundRawPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundRawPacket::encode)
                .decoder(ClientboundRawPacket::decode)
                .consumerMainThread(ForgePlatformServices::handleClient)
                .add();
    }

    private record ServerboundRawPacket(ResourceLocation id, byte[] payload) {
        static void encode(ServerboundRawPacket p, FriendlyByteBuf buf) {
            buf.writeResourceLocation(p.id);
            buf.writeByteArray(p.payload);
        }
        static ServerboundRawPacket decode(FriendlyByteBuf buf) {
            return new ServerboundRawPacket(buf.readResourceLocation(), buf.readByteArray());
        }
    }

    private record ClientboundRawPacket(ResourceLocation id, byte[] payload) {
        static void encode(ClientboundRawPacket p, FriendlyByteBuf buf) {
            buf.writeResourceLocation(p.id);
            buf.writeByteArray(p.payload);
        }
        static ClientboundRawPacket decode(FriendlyByteBuf buf) {
            return new ClientboundRawPacket(buf.readResourceLocation(), buf.readByteArray());
        }
    }

    private static void handleServer(ServerboundRawPacket packet, CustomPayloadEvent.Context ctx) {
        FriendlyByteBuf raw = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(packet.payload));
        ServerPlayer player = ctx.getSender();
        StationBuilder.LOGGER.info("[Network] Server received packet: {}, from: {}", packet.id, player != null ? player.getName().getString() : "Unknown");
        if (player != null) {
            var r = SERVER.get(packet.id);
            if (r != null) r.receive(player, raw);
        }
        ctx.setPacketHandled(true);
    }

    private static void handleClient(ClientboundRawPacket packet, CustomPayloadEvent.Context ctx) {
        FriendlyByteBuf raw = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(packet.payload));
        StationBuilder.LOGGER.info("[Network] Client received raw packet: {}", packet.id);
        var r = CLIENT.get(packet.id);
        if (r != null) {
            r.receive(raw);
        } else {
            StationBuilder.LOGGER.error("[Network] No ClientReceiver registered for ID: {}", packet.id);
        }
        ctx.setPacketHandled(true);
    }

    @Override public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public void registerServerReceiver(ResourceLocation id, PlatformServices.ServerReceiver r) {
        initChannel();
        SERVER.put(id, r);
        StationBuilder.LOGGER.info("[Network] Registered Server Receiver: {}", id);
    }

    @Override public void registerClientReceiver(ResourceLocation id, PlatformServices.ClientReceiver r) {
        initChannel();
        CLIENT.put(id, r);
        StationBuilder.LOGGER.info("[Network] Registered Client Receiver: {}", id);
    }

    @Override public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        initChannel();
        StationBuilder.LOGGER.info("[Network] Sending packet to player {}: {}", player.getName().getString(), id);
        CHANNEL.send(new ClientboundRawPacket(id, readAll(buf)), PacketDistributor.PLAYER.with(player));
    }

    @Override public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        initChannel();
        StationBuilder.LOGGER.info("[Network] Sending packet to Server: {}", id);
        CHANNEL.send(new ServerboundRawPacket(id, readAll(buf)), PacketDistributor.SERVER.noArg());
    }

    private static byte[] readAll(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }
}