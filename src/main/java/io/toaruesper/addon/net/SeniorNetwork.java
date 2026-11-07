package io.toaruesper.addon.net;

import io.toaruesper.addon.ToaruEsper;
import io.toaruesper.addon.client.ClientBlackHoles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The add-on's own network channel. Effects are only sent to players within sight distance of the effect. */
public final class SeniorNetwork {
    /** Bump this when a message changes, so old and new versions refuse to connect instead of misreading data. */
    private static final String PROTOCOL = "2";
    /** Players farther than this (blocks) from an effect are not sent it. */
    private static final double SEND_RANGE = 160.0;

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ToaruEsper.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SeniorNetwork() { }

    public static void register() {
        CHANNEL.messageBuilder(BlackHoleStart.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlackHoleStart::encode).decoder(BlackHoleStart::decode)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlackHoles.start(msg)))
                .add();
        CHANNEL.messageBuilder(BlackHoleEnd.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlackHoleEnd::encode).decoder(BlackHoleEnd::decode)
                .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlackHoles.end(msg)))
                .add();
    }

    public static void sendStart(ServerLevel level, int id, Vec3 at, float radius, int duration, int elapsed) {
        var msg = new BlackHoleStart(id, at.x, at.y, at.z, radius, duration, elapsed);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, SEND_RANGE, level.dimension())), msg);
    }

    public static void sendEnd(ServerLevel level, Vec3 at, int id, boolean collapsed) {
        var msg = new BlackHoleEnd(id, collapsed);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, SEND_RANGE, level.dimension())), msg);
    }
}
