package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void holochest$suppressSneakWhenAiming(CallbackInfo ci) {
        if (HoloChestManager.getInstance().isAimingAtAnyPanel()) {
            Input cur = this.keyPresses;
            if (cur != null && cur.shift()) {
                this.keyPresses = new Input(
                        cur.forward(),
                        cur.backward(),
                        cur.left(),
                        cur.right(),
                        cur.jump(),
                        false,
                        cur.sprint()
                );
            }
        }
    }
}
