package cn.myfrank.stationbuilder.mtr;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Custom payloads used to request and receive an MTR snapshot.
 *
 * <p>The payload is split into {@link DataPayload} chunks so large networks stay below the network
 * packet size limit. Both the request and the chunks carry a token so stale responses from a
 * previous reload can be discarded.</p>
 */
public final class MtrSchedulerNetworking {

    private static final String CHANNEL = "stationbuilder";

    public static final class RequestPayload implements CustomPayload {
        public static final Id<RequestPayload> ID =
                new Id<>(Identifier.of(CHANNEL, "mtr_scheduler_request"));
        public static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC =
                PacketCodec.ofStatic(
                        (buffer, payload) -> buffer.writeInt(payload.token),
                        buffer -> new RequestPayload(buffer.readInt())
                );

        public final int token;

        public RequestPayload(int token) {
            this.token = token;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static final class WritePayload implements CustomPayload {
        public static final Id<WritePayload> ID =
                new Id<>(Identifier.of(CHANNEL, "mtr_scheduler_write"));
        public static final PacketCodec<RegistryByteBuf, WritePayload> CODEC =
                PacketCodec.ofStatic(
                        (buffer, payload) -> {
                            buffer.writeInt(payload.token);
                            buffer.writeString(payload.offsetsJson);
                        },
                        buffer -> new WritePayload(buffer.readInt(), buffer.readString())
                );

        public final int token;
        public final String offsetsJson;

        public WritePayload(int token, String offsetsJson) {
            this.token = token;
            this.offsetsJson = offsetsJson;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static final class DataPayload implements CustomPayload {
        public static final Id<DataPayload> ID =
                new Id<>(Identifier.of(CHANNEL, "mtr_scheduler_data"));
        public static final PacketCodec<RegistryByteBuf, DataPayload> CODEC =
                PacketCodec.ofStatic(
                        (buffer, payload) -> {
                            buffer.writeInt(payload.token);
                            buffer.writeInt(payload.part);
                            buffer.writeInt(payload.parts);
                            buffer.writeBoolean(payload.success);
                            buffer.writeString(payload.error);
                            buffer.writeByteArray(payload.data);
                        },
                        buffer -> new DataPayload(
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readBoolean(),
                                buffer.readString(),
                                buffer.readByteArray()
                        )
                );

        public final int token;
        public final int part;
        public final int parts;
        public final boolean success;
        public final String error;
        public final byte[] data;

        public DataPayload(int token, int part, int parts, boolean success, String error, byte[] data) {
            this.token = token;
            this.part = part;
            this.parts = parts;
            this.success = success;
            this.error = error;
            this.data = data == null ? new byte[0] : data;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private MtrSchedulerNetworking() {
    }
}
