package io.toaruesper.addon.net;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: the black hole ended. collapsed = true plays the shockwave, false just fades it out. */
public record BlackHoleEnd(int id, boolean collapsed) {
    public static void encode(BlackHoleEnd m, FriendlyByteBuf b) {
        b.writeVarInt(m.id);
        b.writeBoolean(m.collapsed);
    }

    public static BlackHoleEnd decode(FriendlyByteBuf b) {
        return new BlackHoleEnd(b.readVarInt(), b.readBoolean());
    }
}
