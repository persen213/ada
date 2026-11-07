package io.blackhole.addon.net.packet;

import io.blackhole.addon.client.ClientBlackHoles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record BlackHoleStartPacket(
    Vec3 center,
    float maxRadius,
    long groupId
) implements CustomPacketPayload {
    public static final Type<BlackHoleStartPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("blackhole", "blackhole_start"));
    public static final StreamCodec<FriendlyByteBuf, BlackHoleStartPacket> STREAM_CODEC = StreamCodec.of(
        BlackHoleStartPacket::encode,
        BlackHoleStartPacket::decode
    );

    public static void encode(BlackHoleStartPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.center.x);
        buf.writeDouble(packet.center.y);
        buf.writeDouble(packet.center.z);
        buf.writeFloat(packet.maxRadius);
        buf.writeLong(packet.groupId);
    }

    public static BlackHoleStartPacket decode(FriendlyByteBuf buf) {
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float maxRadius = buf.readFloat();
        long groupId = buf.readLong();
        return new BlackHoleStartPacket(center, maxRadius, groupId);
    }

    public static void handle(BlackHoleStartPacket packet, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> ClientBlackHoles.spawn(packet.center, packet.maxRadius, packet.groupId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}