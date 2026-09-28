package net.ngsh.shydevelopment.onemoreaudiocontroller.mixin;

import com.mojang.logging.LogUtils;
import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.ngsh.shydevelopment.onemoreaudiocontroller.client.CustomSoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rebuilds the row order of the Music &amp; Sounds screen (Master keeps its own row) from
 * {@code config/onemoreaudiocontroller/orders.json}, mixing vanilla categories and JSON-defined
 * custom controllers. Reloads both JSON configs every time this runs, so edits made externally -
 * or through this same screen when opened via Catalogue's "Config" button - apply immediately.
 */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "getAllSoundOptionsExceptMaster", at = @At("RETURN"), cancellable = true)
    private void onemoreaudiocontroller$rebuildSoundOptions(CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        AudioControllerManager.reload();

        Options options = Minecraft.getInstance().options;
        List<OptionInstance<?>> result = new ArrayList<>();
        for (String id : AudioControllerManager.order()) {
            if (id.equalsIgnoreCase("master")) {
                continue;
            }
            SoundSource vanilla = vanillaSoundSource(id);
            if (vanilla != null) {
                result.add(options.getSoundSourceOptionInstance(vanilla));
                continue;
            }
            AudioControllerManager.ControllerDefinition definition = AudioControllerManager.controllerById(id);
            if (definition != null) {
                result.add(CustomSoundOptions.build(definition));
            } else {
                LOGGER.warn("[onemoreaudiocontroller] Unknown id '{}' in orders.json, skipping", id);
            }
        }
        cir.setReturnValue(result.toArray(new OptionInstance<?>[0]));
    }

    private static SoundSource vanillaSoundSource(String id) {
        try {
            return SoundSource.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
