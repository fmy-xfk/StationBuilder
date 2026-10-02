package cn.myfrank.stationbuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlatformServices {
    public interface ServerReceiver { void receive(ServerPlayer player, FriendlyByteBuf buf); }
    public interface ClientReceiver { void receive(FriendlyByteBuf buf); }
    public interface Impl {
        boolean isModLoaded(String id);
        Path configDir();
        void registerServerReceiver(ResourceLocation id, ServerReceiver receiver);
        void registerClientReceiver(ResourceLocation id, ClientReceiver receiver);
        void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf);
        void sendToServer(ResourceLocation id, FriendlyByteBuf buf);
    }

    private static final Map<ResourceLocation, ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ClientReceiver> CLIENT_RECEIVERS = new ConcurrentHashMap<>();
    private static volatile Impl impl;

    private PlatformServices() {}

    public static void install(Impl value) {
        impl = value;
        SERVER_RECEIVERS.forEach(value::registerServerReceiver);
        CLIENT_RECEIVERS.forEach(value::registerClientReceiver);
    }

    public static boolean isModLoaded(String id) {
        return impl != null && impl.isModLoaded(id);
    }

    public static Path configDir() {
        if (impl == null) throw new IllegalStateException("PlatformServices is not installed");
        return impl.configDir();
    }

    public static void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
        SERVER_RECEIVERS.put(id, receiver);
        if (impl != null) impl.registerServerReceiver(id, receiver);
    }

    public static void registerClientReceiver(ResourceLocation id, ClientReceiver receiver) {
        CLIENT_RECEIVERS.put(id, receiver);
        if (impl != null) impl.registerClientReceiver(id, receiver);
    }

    public static void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        requireImpl().sendToPlayer(player, id, buf);
    }

    public static void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        requireImpl().sendToServer(id, buf);
    }

    private static Impl requireImpl() {
        if (impl == null) throw new IllegalStateException("PlatformServices is not installed");
        return impl;
    }
}
