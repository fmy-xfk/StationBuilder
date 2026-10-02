package cn.myfrank.stationbuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class ForgePlatformServices implements PlatformServices.Impl {
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(StationBuilder.MOD_ID, "main"), // 修正为 new ResourceLocation
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static final Map<ResourceLocation, PlatformServices.ServerReceiver> SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PlatformServices.ClientReceiver> CLIENT = new ConcurrentHashMap<>();
    private static boolean initialized;

    static void initChannel() {
        if (initialized) return;
        initialized = true;

        StationBuilder.LOGGER.info("[Network] Initializing Network Channel...");

        // 2. 修正：1.20.1 使用 CHANNEL.registerMessage 进行消息序列号注册
        CHANNEL.registerMessage(0, ServerboundRawPacket.class,
                ServerboundRawPacket::encode,
                ServerboundRawPacket::decode,
                ForgePlatformServices::handleServer);

        CHANNEL.registerMessage(1, ClientboundRawPacket.class,
                ClientboundRawPacket::encode,
                ClientboundRawPacket::decode,
                ForgePlatformServices::handleClient);
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

    private static void handleServer(ServerboundRawPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            FriendlyByteBuf raw = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(packet.payload));
            ServerPlayer player = ctx.getSender();
            StationBuilder.LOGGER.info("[Network] Server received packet: {}, from: {}", packet.id, player != null ? player.getName().getString() : "Unknown");
            if (player != null) {
                var r = SERVER.get(packet.id);
                if (r != null) r.receive(player, raw);
            }
        });
        ctx.setPacketHandled(true); // 声明此包已被正常消费和处理
    }

    private static void handleClient(ClientboundRawPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            FriendlyByteBuf raw = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(packet.payload));
            StationBuilder.LOGGER.info("[Network] Client received raw packet: {}", packet.id);
            var r = CLIENT.get(packet.id);
            if (r != null) {
                r.receive(raw);
            } else {
                StationBuilder.LOGGER.error("[Network] No ClientReceiver registered for ID: {}", packet.id);
            }
        });
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundRawPacket(id, readAll(buf)));
    }

    @Override public void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        initChannel();
        StationBuilder.LOGGER.info("[Network] Sending packet to Server: {}", id);
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new ServerboundRawPacket(id, readAll(buf)));
    }

    private static byte[] readAll(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }
}