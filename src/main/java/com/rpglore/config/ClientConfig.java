package com.rpglore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_LORE_ID_IN_TOOLTIP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client display settings").push("display");

        SHOW_LORE_ID_IN_TOOLTIP = builder
                .comment("Show the internal lore_id in the book tooltip (useful for pack authors)")
                .define("showLoreIdInTooltip", false);

        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
