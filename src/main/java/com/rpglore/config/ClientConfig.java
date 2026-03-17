package com.rpglore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_PICKUP_TOAST;
    public static final ForgeConfigSpec.BooleanValue SHOW_LORE_ID_IN_TOOLTIP;
    public static final ForgeConfigSpec.ConfigValue<String> GLINT_COLOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client display settings").push("display");

        SHOW_PICKUP_TOAST = builder
                .comment("Show a toast notification when picking up a lore book")
                .define("showPickupToast", true);

        SHOW_LORE_ID_IN_TOOLTIP = builder
                .comment("Show the internal lore_id in the book tooltip (useful for pack authors)")
                .define("showLoreIdInTooltip", false);

        GLINT_COLOR = builder
                .comment("Enchantment glint color override. Use 'default' for vanilla purple, or a hex color like 'FF8800'. (Reserved for future use)")
                .define("glintColor", "default");

        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
