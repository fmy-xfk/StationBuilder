package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.scheduler.PayloadCompression;
import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/** Client side of the in-game MTR snapshot transfer (NeoForge 1.21.x). */
public final class MtrSchedulerClientTransport implements SchedulerSession.Transport {

    private static final Object ASSEMBLY_LOCK = new Object();
    private static final Map<Integer, byte[][]> CHUNKS = new HashMap<>();
    private static final Map<Integer, Integer> RECEIVED = new HashMap<>();

    @Override
    public void request(int token) {
        PacketDistributor.sendToServer(new MtrSchedulerNetworking.RequestPayload(token));
    }

    @Override
    public void write(int token, Map<String, Long> offsets) {
        PacketDistributor.sendToServer(new MtrSchedulerNetworking.WritePayload(
                token,
                Json.writeCompact(offsets)
        ));
    }

    public static void handle(MtrSchedulerNetworking.DataPayload payload) {
        SchedulerSession session = SchedulerSession.get();
        if (!payload.success()) {
            synchronized (ASSEMBLY_LOCK) {
                CHUNKS.remove(payload.token());
                RECEIVED.remove(payload.token());
            }
            session.acceptError(payload.token(), payload.error());
            return;
        }

        byte[] complete;
        synchronized (ASSEMBLY_LOCK) {
            int token = payload.token();
            int parts = payload.parts();
            int part = payload.part();
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
                chunks[part] = payload.data();
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
            session.acceptSnapshot(payload.token(), stations, departures);
        } catch (RuntimeException exception) {
            session.acceptError(payload.token(), "Could not read MTR data: " + exception.getMessage());
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
