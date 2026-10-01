package com.jsorrell.carpetskyadditions.config;

/**
 * Simple in-code configuration (replaces the previous Cloth Config based configuration so the mod
 * has no hard dependency on cloth-config2). Values can be adjusted here before building.
 */
public final class SkyAdditionsConfig {
    public enum InitialTreeType {
        OAK("Oak"),
        ACACIA("Acacia");

        private final String name;

        InitialTreeType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static InitialTreeType parse(String value) {
            return switch (value.toLowerCase()) {
                case "oak" -> OAK;
                case "acacia" -> ACACIA;
                default -> throw new IllegalArgumentException("Unknown tree type: " + value);
            };
        }
    }

    private static final SkyAdditionsConfig INSTANCE = new SkyAdditionsConfig();

    public boolean defaultToSkyBlockWorld = false;
    public boolean enableDatapackByDefault = false;
    public String initialTreeType = InitialTreeType.OAK.toString();
    public boolean autoEnableDefaultSettings = true;
    /**
     * Language used for advancement names/descriptions. When set to a language code such as
     * "zh_cn", the server translates the mod's advancement text itself and sends plain text to
     * clients, so clients WITHOUT the mod or the language resource pack still see the correct
     * language. Set to "client" to keep vanilla behaviour (the client translates using its own
     * language). Available codes: zh_cn, zh_tw, en_us, es_es, fi_fi, fr_fr, it_it, ko_kr,
     * nl_nl, pt_br, ru_ru.
     */
    public String advancementLanguage = "zh_cn";

    private SkyAdditionsConfig() {}

    public static SkyAdditionsConfig get() {
        return INSTANCE;
    }

    public InitialTreeType getInitialTreeType() {
        return InitialTreeType.parse(initialTreeType);
    }
}
