package cn.myfrank.stationbuilder.mtr;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** NeoForge payloads for the in-game MTR snapshot transfer. */
public final class MtrSchedulerNetworking {

    private static final String CHANNEL = "stationbuilder";

    public record RequestPayload(int token) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CHANNEL, "mtr_scheduler_request"));
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeInt(payload.token()),
                        buffer -> new RequestPayload(buffer.readInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WritePayload(int token, String offsetsJson) implements CustomPacketPayload {
        public static final Type<WritePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CHANNEL, "mtr_scheduler_write"));
        public static final StreamCodec<FriendlyByteBuf, WritePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeInt(payload.token());
                            buffer.writeUtf(payload.offsetsJson());
                        },
                        buffer -> new WritePayload(buffer.readInt(), buffer.readUtf())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DataPayload(
            int token,
            int part,
            int parts,
            boolean success,
            String error,
            byte[] data
    ) implements CustomPacketPayload {
        public static final Type<DataPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(CHANNEL, "mtr_scheduler_data"));
        public static final StreamCodec<FriendlyByteBuf, DataPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeInt(payload.token());
                            buffer.writeInt(payload.part());
                            buffer.writeInt(payload.parts());
                            buffer.writeBoolean(payload.success());
                            buffer.writeUtf(payload.error());
                            buffer.writeByteArray(payload.data());
                        },
                        buffer -> new DataPayload(
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readInt(),
                                buffer.readBoolean(),
                                buffer.readUtf(),
                                buffer.readByteArray()
                        )
                );

        public DataPayload {
            data = data == null ? new byte[0] : data;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MtrSchedulerNetworking() {
    }
}
