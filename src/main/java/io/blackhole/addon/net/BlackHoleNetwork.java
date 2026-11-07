package io.blackhole.addon.net;

import io.blackhole.addon.client.ClientBlackHoles;
import io.blackhole.addon.net.packet.BlackHoleStartPacket;
import io.blackhole.addon.net.packet.BlackHoleEndPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BlackHoleNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL;

    static {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("blackhole", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
        );

        int index = 0;
        CHANNEL.messageBuilder(BlackHoleStartPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(BlackHoleStartPacket::encode)
            .decoder(BlackHoleStartPacket::decode)
            .consumerMainThread(BlackHoleStartPacket::handle)
            .add();
        CHANNEL.messageBuilder(BlackHoleEndPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(BlackHoleEndPacket::encode)
            .decoder(BlackHoleEndPacket::decode)
            .consumerMainThread(BlackHoleEndPacket::handle)
            .add();
    }

    public static void init() {}

    public static void sendStart(Vec3 center, float maxRadius, long groupId) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.NEAR.with(() ->
            new net.minecraftforge.network.PacketDistributor.TargetPoint(center.x, center.y, center.z, 160, net.minecraft.core.RegistryDimensionOverworld.OVERWORLD.dimension())),
            new BlackHoleStartPacket(center, maxRadius, groupId));
    }

    public static void sendEnd(long groupId) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new BlackHoleEndPacket(groupId));
    }
}