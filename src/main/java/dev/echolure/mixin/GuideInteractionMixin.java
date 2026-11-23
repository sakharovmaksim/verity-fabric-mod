package dev.echolure.mixin;

import dev.echolure.EchoLure;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Allay.class)
abstract class GuideInteractionMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void echoLure$skipPredictedEquipment(Player player, InteractionHand hand,
                                                CallbackInfoReturnable<InteractionResult> callback) {
        var allay = (Allay) (Object) this;
        var name = allay.getCustomName();
        // The interaction packet has already been sent. Leave equipment and gift handling to the server.
        if (allay.level().isClientSide() && name != null && name.getContents() instanceof TranslatableContents text
            && EchoLure.GUIDE_NAME_KEY.equals(text.getKey())) {
            callback.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
