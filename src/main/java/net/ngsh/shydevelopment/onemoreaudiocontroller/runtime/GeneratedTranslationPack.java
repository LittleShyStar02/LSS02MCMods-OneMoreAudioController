package net.ngsh.shydevelopment.onemoreaudiocontroller.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.ngsh.shydevelopment.onemoreaudiocontroller.AudioControllerManager;
import net.ngsh.shydevelopment.onemoreaudiocontroller.OneMoreAudioController;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Generates a tiny built-in resource pack, on disk, containing one {@code soundCategory.<id>}
 * translation per known controller - so consumers like FancyMenu that just call
 * {@code Component.translatable("soundCategory." + id)} with no fallback of their own show the
 * controller's {@code default_name} instead of the raw translation key. Registered through
 * {@link AddPackFindersEvent} as required, hidden and at the bottom of the pack list, so it's always
 * active, never shows up in the Resource Packs screen, and never outranks a real translation.
 *
 * <p>Deliberately a real resource pack instead of patching {@link net.minecraft.locale.Language}'s
 * live translation table directly: going through the normal pack pipeline means a real translation -
 * from a proper lang file or an actual resource pack, in any language - still wins for free, using
 * the exact same precedence rules that already decide between two ordinary resource packs, instead of
 * this mod having to reimplement "don't overwrite an existing entry" by hand.
 */
public final class GeneratedTranslationPack {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_ID = "onemoreaudiocontroller_generated_lang";

    /** Last content written to {@link #langFile()}, so routine {@link #regenerate()} calls that don't
     *  actually change anything (most {@code recompute()} runs) skip the disk write and, more
     *  importantly, the resource reload entirely. */
    private static volatile String lastWrittenJson = null;

    /** Whether {@link #regenerate()} has ever run before; see the comment at the bottom of that
     *  method for why the very first call is special-cased. */
    private static volatile boolean everRegenerated = false;

    private GeneratedTranslationPack() {
    }

    private static Path packRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(OneMoreAudioController.MODID).resolve("generated_resourcepack");
    }

    private static Path langFile() {
        return packRoot().resolve("assets").resolve(OneMoreAudioController.MODID).resolve("lang").resolve("en_us.json");
    }

    private static Path packMcmeta() {
        return packRoot().resolve("pack.mcmeta");
    }

    /** Registered on the mod event bus in the mod constructor; wires the pack above into Minecraft's
     *  own pack repository so it loads automatically, like any other built-in resource pack. */
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(onLoad -> onLoad.accept(new Pack(
                new PackLocationInfo(
                        PACK_ID,
                        Component.literal("One More Audio Controller (generated translations)"),
                        PackSource.BUILT_IN,
                        Optional.empty()),
                new PathPackResources.PathResourcesSupplier(packRoot()),
                new Pack.Metadata(
                        Component.literal("Generated controller name fallbacks"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.DEFAULT_FLAGS,
                        List.of(),
                        true),
                new PackSelectionConfig(true, Pack.Position.BOTTOM, true)
        )));
    }

    /**
     * Rewrites the generated lang file from the controller set {@link AudioControllerManager} has
     * right now, and - if the game is already running - reloads resource packs so the change shows up
     * immediately, instead of only after a restart. A no-op (no write, no reload) when the content
     * would be identical to what's already on disk, so calling this from every
     * {@code AudioControllerManager.recompute()} - including the very first one, at boot, before
     * {@code Minecraft.getInstance()} exists - is cheap and safe.
     */
    public static synchronized void regenerate() {
        boolean firstCallEver = !everRegenerated;
        everRegenerated = true;

        Map<String, String> translations = new TreeMap<>(AudioControllerManager.allDefaultNames());
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            json.addProperty("soundCategory." + entry.getKey(), entry.getValue());
        }
        String content = GSON.toJson(json);
        if (content.equals(lastWrittenJson)) {
            return;
        }

        try {
            Files.createDirectories(langFile().getParent());
            Files.writeString(langFile(), content);
            ensureMcmeta();
        } catch (IOException e) {
            LOGGER.error("[onemoreaudiocontroller] Failed to write the generated translations resource pack", e);
            return;
        }
        lastWrittenJson = content;

        // The very first regenerate() ever (from AudioControllerManager.reload() in the mod
        // constructor) runs before Minecraft's own "initial" resource-pack load has happened at all -
        // that load reads this file fresh off disk on its own, for free, same as any other built-in
        // pack, as long as it's already written by then (which it is: this runs during mod
        // construction, well before that first load). Forcing an explicit reload here too would be
        // both redundant and, this early in NeoForge's own mod-loading sequence, actively unsafe: it
        // re-enters that same loading cycle mid-flight and can run *other* mods' still-queued
        // client-setup work early or in a half-set-up state - this is exactly what let an unrelated
        // mod (Create) crash on a registry lookup, and what let FancyMenu crash on a null
        // LanguageManager, during testing. Every later call (renaming/adding/reordering a controller
        // in-game, or another mod registering one via the API at runtime) happens long after boot, with
        // no such cycle to re-enter, so it keeps reloading immediately like toggling a pack in the
        // vanilla screen does.
        if (firstCallEver) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.reloadResourcePacks();
        }
    }

    private static void ensureMcmeta() throws IOException {
        if (Files.exists(packMcmeta())) {
            return;
        }
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Generated by One More Audio Controller");
        pack.addProperty("pack_format", 48);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(packMcmeta(), GSON.toJson(root));
    }
}
