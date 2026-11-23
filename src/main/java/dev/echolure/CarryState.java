package dev.echolure;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

record CarryState(boolean carrying) implements CustomPacketPayload {
    static final Type<CarryState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("echo_lure", "carry_state"));
    static final StreamCodec<RegistryFriendlyByteBuf, CarryState> CODEC = StreamCodec.of(
        (buffer, state) -> buffer.writeBoolean(state.carrying()), buffer -> new CarryState(buffer.readBoolean()));

    static void send(ServerPlayer player, boolean carrying) {
        if (ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, new CarryState(carrying));
    }

    @Override public Type<CarryState> type() { return TYPE; }
}
