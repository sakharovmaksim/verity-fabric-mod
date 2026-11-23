package dev.echolure.mixin;

import dev.echolure.EchoLureClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class CarryUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void echoLure$throwCarriedGuide(CallbackInfo callback) {
        if (EchoLureClient.useCarriedGuide()) callback.cancel();
    }
}
