package net.ngsh.shydevelopment.onemoreaudiocontroller;

import net.ngsh.shydevelopment.onemoreaudiocontroller.client.gui.ControllerManagerScreen;
import net.ngsh.shydevelopment.onemoreaudiocontroller.runtime.GeneratedTranslationPack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This mod only touches client-side audio/GUI code, so it's declared client-only: NeoForge won't
// load this class (and therefore won't load the mod at all, since it has no other @Mod class) on
// dedicated servers.
@Mod(value = OneMoreAudioController.MODID, dist = Dist.CLIENT)
public class OneMoreAudioController {

    public static final String MODID = "onemoreaudiocontroller";

    public OneMoreAudioController(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(GeneratedTranslationPack::addPackFinders);
        modEventBus.addListener(OneMoreAudioController::onClientSetup);

        // Pure disk I/O (reads controllers.json/orders.json, writes the generated lang file) - no
        // Minecraft/Options dependency, so it's safe here, and it has to run this early: it needs to
        // be done before Minecraft's own "initial" resource-pack load (which happens shortly after
        // this constructor returns, while the loading screen is still up) so that load picks up the
        // freshly-written translations for free, with no extra reload call needed at all - see
        // GeneratedTranslationPack.regenerate() for why forcing one here specifically caused crashes.
        AudioControllerManager.reload();

        // Lets external mod-list GUIs (Catalogue, NeoForge's own Mods screen "Config" button, ...)
        // open a settings screen for this mod. We open our own controller manager screen, which
        // lets the player add/rename/delete/reorder controllers in-game, with a shortcut from there
        // into the vanilla Sound Options screen to actually move the sliders.
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, modListScreen) -> new ControllerManagerScreen(modListScreen)
        );
    }

    // promoteConfiguredControllersAtBoot() touches Minecraft.options/SoundSource, so it stays
    // deferred past mod construction to FMLClientSetupEvent's enqueueWork(), once client setup's
    // parallel mod work is done and Minecraft is reliably fully set up - see that method's own doc
    // for why this specific piece needs that later, safer point. It doesn't write the resource pack
    // or trigger a reload, so - unlike AudioControllerManager.reload() - it has no reason to run any
    // earlier than this.
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AudioControllerManager::promoteConfiguredControllersAtBoot);
    }
}
