package io.toaruesper.addon.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client: a black hole is open. Only what is needed to draw it.
 * elapsed = how many ticks it has already been open. The server repeats this message every 2 seconds,
 * so players who arrive late (or miss a message) still see the hole at the right moment of its life.
 */
public record BlackHoleStart(int id, double x, double y, double z, float radius, int duration, int elapsed) {
    public static void encode(BlackHoleStart m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeFloat(m.radius);
        b.writeVarInt(m.duration);
        b.writeVarInt(m.elapsed);
    }

    public static BlackHoleStart decode(FriendlyByteBuf b) {
        return new BlackHoleStart(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readVarInt(), b.readVarInt());
    }
}
