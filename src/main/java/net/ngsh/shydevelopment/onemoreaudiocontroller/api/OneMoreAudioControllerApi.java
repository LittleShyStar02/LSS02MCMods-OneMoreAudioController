package net.ngsh.shydevelopment.onemoreaudiocontroller.api;

import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;

/**
 * Public API for other mods to add their own audio controller - a new independent slider on the
 * vanilla "Music &amp; Sounds" options screen - entirely from code, with no JSON file to ship.
 *
 * <p><b>Only the mod that owns a sound knows which controller it belongs to</b>, so sound events
 * are always assigned here, in code, never in this mod's {@code controllers.json}. That JSON file
 * only lets modpacks/resource packs declare an id's display name and ordering; it can never attach
 * sounds to a controller. If you want a working slider, register it with this class.
 *
 * <p>Example: a guns mod wants its own "Gun Sounds" slider, independent from Players/Blocks/etc:
 * <pre>{@code
 * OneMoreAudioControllerApi.registerController(
 *         "mygunmod_gun_sounds",
 *         "Gun Sounds",
 *         ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_shot"),
 *         ResourceLocation.fromNamespaceAndPath("mygunmod", "gun_reload")
 * );
 * }</pre>
 *
 * <p>Call this once, early - a mod constructor or {@code FMLCommonSetupEvent} both work, since the
 * final on-screen order is only computed the first time the player opens the Sound Options screen,
 * well after every mod has finished loading.
 *
 * <p>Controllers registered this way are mirrored (id, default name, sounds, current volume) to
 * {@code config/onemoreaudiocontroller/externalcontroller.json} on every game start, purely so
 * modpack authors editing {@code controllers.json} by hand can see which ids are already taken and
 * avoid picking the same one. That file is never read back as a source of controllers - only your
 * code is. If a JSON controller and an API controller ever share an id, the API one always wins and
 * the JSON one is skipped with a warning in the log.
 *
 * <p>The label shown on screen is {@code "Gun Sounds"} (the {@code defaultName} you pass) unless a
 * resource pack or another mod's lang file supplies a translation for {@code soundCategory.<id>} in
 * the player's current language - that's the only thing a resource pack can contribute for a
 * controller, since it can't reach into {@code config/} to add sounds or change ids.
 *
 * <p>Pick an id that includes your own mod id (e.g. {@code "mygunmod_gun_sounds"}) to avoid clashing
 * with other mods or with this mod's own reserved vanilla category names
 * (master/music/records/weather/blocks/hostile/neutral/players/ambient/voice).
 */
public final class OneMoreAudioControllerApi {

    private OneMoreAudioControllerApi() {
    }

    /**
     * Registers a controller with an auto-generated label (id with underscores/dashes turned into
     * spaces and title-cased, e.g. {@code "gun_sounds"} becomes {@code "Gun Sounds"}). Prefer the
     * overload with an explicit {@code defaultName} for anything user-facing.
     *
     * @param id     unique id for this controller, see the class doc for naming advice
     * @param sounds every sound event this slider should control the volume of
     */
    public static void registerController(String id, ResourceLocation... sounds) {
        registerController(id, null, sounds);
    }

    /**
     * Registers a controller with an explicit English fallback label.
     *
     * @param id          unique id for this controller, see the class doc for naming advice
     * @param defaultName English label shown when no translation exists for {@code soundCategory.<id>}
     * @param sounds      every sound event this slider should control the volume of
     */
    public static void registerController(String id, String defaultName, ResourceLocation... sounds) {
        List<ResourceLocation> soundList = sounds == null ? List.of() : Arrays.asList(sounds);
        AudioControllerManager.registerApiController(id, defaultName, soundList);
    }
}
