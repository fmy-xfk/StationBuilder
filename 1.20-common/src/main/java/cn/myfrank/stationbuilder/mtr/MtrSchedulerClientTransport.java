package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.StationBuilder;
import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.scheduler.PayloadCompression;
import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/** Client side of the in-game MTR snapshot transfer (Minecraft 1.20.x). */
public final class MtrSchedulerClientTransport implements SchedulerSession.Transport {

    private static final Object ASSEMBLY_LOCK = new Object();
    private static final Map<Integer, byte[][]> CHUNKS = new HashMap<>();
    private static final Map<Integer, Integer> RECEIVED = new HashMap<>();

    private static boolean receiverRegistered;

    public static void registerReceiver() {
        if (receiverRegistered) {
            return;
        }
        receiverRegistered = true;
        ClientPlayNetworking.registerGlobalReceiver(StationBuilder.MTR_SCHEDULER_DATA,
                (client, handler, buffer, sender) -> {
                    int token = buffer.readInt();
                    int part = buffer.readInt();
                    int parts = buffer.readInt();
                    boolean success = buffer.readBoolean();
                    String error = buffer.readString();
                    byte[] data = buffer.readByteArray();
                    client.execute(() -> handle(token, part, parts, success, error, data));
                });
    }

    @Override
    public void request(int token) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(token);
        ClientPlayNetworking.send(StationBuilder.MTR_SCHEDULER_REQUEST, buffer);
    }

    @Override
    public void write(int token, Map<String, Long> offsets) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(token);
        buffer.writeString(Json.writeCompact(offsets));
        ClientPlayNetworking.send(StationBuilder.MTR_SCHEDULER_WRITE, buffer);
    }

    private static void handle(int token, int part, int parts, boolean success, String error, byte[] data) {
        SchedulerSession session = SchedulerSession.get();
        if (!success) {
            synchronized (ASSEMBLY_LOCK) {
                CHUNKS.remove(token);
                RECEIVED.remove(token);
            }
            session.acceptError(token, error);
            return;
        }

        byte[] complete;
        synchronized (ASSEMBLY_LOCK) {
            if (parts <= 0 || part < 0 || part >= parts) {
                session.acceptError(token, "Invalid MTR snapshot chunk");
                return;
            }
            byte[][] chunks = CHUNKS.get(token);
            if (chunks == null || chunks.length != parts) {
                chunks = new byte[parts][];
                CHUNKS.put(token, chunks);
                RECEIVED.put(token, 0);
            }
            if (chunks[part] == null) {
                chunks[part] = data;
                RECEIVED.merge(token, 1, Integer::sum);
            }
            if (RECEIVED.getOrDefault(token, 0) < parts) {
                return;
            }
            complete = join(chunks);
            CHUNKS.remove(token);
            RECEIVED.remove(token);
        }

        try {
            String envelope = PayloadCompression.decompress(complete);
            Map<String, Object> root = Json.object(Json.parse(envelope));
            String stations = Json.string(root.get("stations"), "");
            String departures = Json.string(root.get("departures"), "");
            session.acceptSnapshot(token, stations, departures);
        } catch (RuntimeException exception) {
            session.acceptError(token, "Could not read MTR data: " + exception.getMessage());
        }
    }

    private static byte[] join(byte[][] chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            if (chunk != null) {
                output.writeBytes(chunk);
            }
        }
        return output.toByteArray();
    }
}
