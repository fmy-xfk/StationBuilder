package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.scheduler.PayloadCompression;
import cn.myfrank.stationbuilder.scheduler.SchedulerSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the in-game MTR snapshot transfer.
 *
 * <p>Sends the C2S request and reassembles chunked S2C payloads before handing the decompressed
 * JSON to {@link SchedulerSession}. Data is only assembled on the client thread.</p>
 */
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
        ClientPlayNetworking.registerGlobalReceiver(MtrSchedulerNetworking.DataPayload.ID,
                (payload, context) -> context.client().execute(() -> handle(payload)));
    }

    @Override
    public void request(int token) {
        ClientPlayNetworking.send(new MtrSchedulerNetworking.RequestPayload(token));
    }

    @Override
    public void write(int token, Map<String, Long> offsets) {
        ClientPlayNetworking.send(new MtrSchedulerNetworking.WritePayload(
                token,
                Json.writeCompact(offsets)
        ));
    }

    private static void handle(MtrSchedulerNetworking.DataPayload payload) {
        SchedulerSession session = SchedulerSession.get();
        if (!payload.success) {
            synchronized (ASSEMBLY_LOCK) {
                CHUNKS.remove(payload.token);
                RECEIVED.remove(payload.token);
            }
            session.acceptError(payload.token, payload.error);
            return;
        }

        byte[] complete = null;
        synchronized (ASSEMBLY_LOCK) {
            if (payload.parts <= 0 || payload.part < 0 || payload.part >= payload.parts) {
                session.acceptError(payload.token, "Invalid MTR snapshot chunk");
                return;
            }
            byte[][] chunks = CHUNKS.get(payload.token);
            if (chunks == null || chunks.length != payload.parts) {
                chunks = new byte[payload.parts][];
                CHUNKS.put(payload.token, chunks);
                RECEIVED.put(payload.token, 0);
            }
            if (chunks[payload.part] == null) {
                chunks[payload.part] = payload.data;
                RECEIVED.merge(payload.token, 1, Integer::sum);
            }
            if (RECEIVED.getOrDefault(payload.token, 0) < payload.parts) {
                return;
            }
            complete = join(chunks);
            CHUNKS.remove(payload.token);
            RECEIVED.remove(payload.token);
        }

        try {
            String envelope = PayloadCompression.decompress(complete);
            Map<String, Object> root = Json.object(Json.parse(envelope));
            String stations = Json.string(root.get("stations"), "");
            String departures = Json.string(root.get("departures"), "");
            session.acceptSnapshot(payload.token, stations, departures);
        } catch (RuntimeException exception) {
            session.acceptError(payload.token, "Could not read MTR data: " + exception.getMessage());
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
