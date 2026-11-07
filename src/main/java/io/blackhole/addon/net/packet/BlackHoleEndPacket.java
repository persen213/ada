package io.blackhole.addon.net.packet;

import io.blackhole.addon.client.ClientBlackHoles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackHoleEndPacket(
    long groupId
) implements CustomPacketPayload {
    public static final Type<BlackHoleEndPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("blackhole", "blackhole_end"));
    public static final StreamCodec<FriendlyByteBuf, BlackHoleEndPacket> STREAM_CODEC = StreamCodec.of(
        BlackHoleEndPacket::encode,
        BlackHoleEndPacket::decode
    );

    public static void encode(BlackHoleEndPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.groupId);
    }

    public static BlackHoleEndPacket decode(FriendlyByteBuf buf) {
        return new BlackHoleEndPacket(buf.readLong());
    }

    public static void handle(BlackHoleEndPacket packet, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> ClientBlackHoles.end(packet.groupId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}