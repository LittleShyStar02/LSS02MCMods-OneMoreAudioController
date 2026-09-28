package net.ngsh.shydevelopment.onemoreaudiocontroller.mixin;

import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects the volume of any sound covered by a JSON- or API-defined controller (see
 * {@link AudioControllerManager}) to that controller's slider, instead of whatever vanilla
 * {@link net.minecraft.sounds.SoundSource} category the sound actually plays under - this is what
 * lets a controller affect a sound regardless of which category its owning mod tagged it with.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void onemoreaudiocontroller$customVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        AudioControllerManager.ControllerDefinition definition = AudioControllerManager.findBySound(sound.getLocation());
        if (definition == null) {
            return;
        }

        double controllerVolume = definition.volume;
        // Once a controller is a real SoundSource (see AudioControllerManager#promote), the slider
        // the player actually sees and moves is the native Music & Sounds one for that category
        // (SoundOptionsScreenMixin renders it via Options#getSoundSourceOptionInstance), not this
        // mod's own one - definition.volume would otherwise stay frozen at whatever it was when the
        // controller got promoted, ignoring every change the player makes afterwards.
        SoundSource realSource = AudioControllerManager.realSource(definition.id);
        if (realSource != null) {
            controllerVolume = Minecraft.getInstance().options.getSoundSourceVolume(realSource);
        }

        cir.setReturnValue(Mth.clamp(sound.getVolume() * (float) controllerVolume, 0.0F, 1.0F));
    }
}
