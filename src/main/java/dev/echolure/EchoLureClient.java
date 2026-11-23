package dev.echolure;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public final class EchoLureClient implements ClientModInitializer {
    private static boolean carrying;
    private static boolean useWasDown;
    private static boolean throwing;

    public static boolean useCarriedGuide() {
        if (!carrying && !throwing) return false;
        var client = Minecraft.getInstance();
        if (carrying && !throwing && !useWasDown && client.player != null) {
            client.player.connection.sendCommand("yellow throw");
            throwing = true;
        }
        return true;
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Actors.WATCHER, WatcherRenderer::new);
        EntityRendererRegistry.register(Actors.BOX, BoxRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CarryState.TYPE, (state, context) -> {
            carrying = state.carrying();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            carrying = false;
            throwing = false;
            useWasDown = false;
        });
        var throwKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.echo_lure.throw", InputConstants.KEY_G, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            useWasDown = client.options.keyUse.isDown();
            if (!useWasDown) throwing = false;
            while (throwKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.player.connection.sendCommand("yellow throw");
                }
            }
        });
    }
}
