package com.rpglore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_LORE_ID_IN_TOOLTIP;

    // --- Codex display settings ---
    public static final ForgeConfigSpec.BooleanValue CODEX_SHOW_NOTIFICATION;
    public static final ForgeConfigSpec.BooleanValue CODEX_PLAY_SOUND;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client display settings").push("display");

        SHOW_LORE_ID_IN_TOOLTIP = builder
                .comment("Show the internal lore_id in the book tooltip (useful for pack authors)")
                .define("showLoreIdInTooltip", false);

        builder.pop();

        builder.comment("Codex display settings").push("codex_display");

        CODEX_SHOW_NOTIFICATION = builder
                .comment("Show an action bar message when a new book is added to the Codex")
                .define("showCollectionNotification", true);

        CODEX_PLAY_SOUND = builder
                .comment("Play a sound when a new book is collected into the Codex")
                .define("playCollectionSound", true);

        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
