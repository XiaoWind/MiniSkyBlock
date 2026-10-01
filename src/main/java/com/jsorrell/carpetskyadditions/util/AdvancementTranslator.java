package com.jsorrell.carpetskyadditions.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsorrell.carpetskyadditions.config.SkyAdditionsConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

/**
 * Server-side advancement translation.
 *
 * <p>The mod's advancement JSONs use the English text itself as the translation key
 * ({@code {"translate": "English text"}}). On a client without the mod (or the language
 * resource pack) that text can never be resolved, so the advancement displays in English
 * regardless of the client language. This class rewrites those components into plain literal
 * text on the server, using the language configured via
 * {@link SkyAdditionsConfig#advancementLanguage}, before the advancement tree is sent to
 * clients. Components whose key is not part of the chosen language file (e.g. vanilla
 * {@code advancements.*} keys) are left untouched so the client still translates them itself.
 */
public final class AdvancementTranslator {
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private AdvancementTranslator() {}

    /** Rewrites the display titles/descriptions of every advancement in the given map in place. */
    public static void translateAll(Map<Identifier, Advancement> advancements) {
        String language = SkyAdditionsConfig.get().advancementLanguage;
        if (language == null || language.isBlank() || "client".equalsIgnoreCase(language.trim())) {
            return;
        }
        Map<String, String> translations = translationsFor(language.trim());
        if (translations.isEmpty()) {
            return;
        }
        advancements.replaceAll((id, advancement) -> translate(advancement, translations));
    }

    private static Map<String, String> translationsFor(String language) {
        return CACHE.computeIfAbsent(language, AdvancementTranslator::loadTranslations);
    }

    private static Map<String, String> loadTranslations(String language) {
        String path = "/assets/miniskyblock/lang/" + language + ".json";
        try (InputStream in = AdvancementTranslator.class.getResourceAsStream(path)) {
            if (in == null) {
                return Map.of();
            }
            JsonObject root =
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            Map<String, String> translations = new HashMap<>();
            root.entrySet()
                    .forEach(entry -> translations.put(entry.getKey(), entry.getValue().getAsString()));
            return translations;
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static Advancement translate(Advancement advancement, Map<String, String> translations) {
        Optional<DisplayInfo> display = advancement.display();
        if (display.isEmpty()) {
            return advancement;
        }
        DisplayInfo info = display.get();
        Component title = translate(info.getTitle(), translations);
        Component description = translate(info.getDescription(), translations);
        if (title == info.getTitle() && description == info.getDescription()) {
            return advancement;
        }
        DisplayInfo newDisplay =
                new DisplayInfo(
                        info.getIcon(),
                        title,
                        description,
                        info.getBackground(),
                        info.getType(),
                        info.shouldShowToast(),
                        info.shouldAnnounceChat(),
                        info.isHidden());
        newDisplay.setLocation(info.getX(), info.getY());
        // The 6-arg constructor recomputes the (styled) display name from the new display.
        return new Advancement(
                advancement.parent(),
                Optional.of(newDisplay),
                advancement.rewards(),
                advancement.criteria(),
                advancement.requirements(),
                advancement.sendsTelemetryEvent());
    }

    private static Component translate(Component component, Map<String, String> translations) {
        if (component.getContents() instanceof TranslatableContents contents
                && contents.getArgs().length == 0) {
            String translated = translations.get(contents.getKey());
            if (translated != null) {
                MutableComponent literal = Component.literal(translated);
                return literal.setStyle(component.getStyle());
            }
        }
        return component;
    }
}
