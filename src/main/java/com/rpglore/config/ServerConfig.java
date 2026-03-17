package com.rpglore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue GLOBAL_DROP_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ONLY_HOSTILE_MOBS;
    public static final ForgeConfigSpec.IntValue MAX_BOOKS_PER_KILL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PER_BOOK_WEIGHTS;
    public static final ForgeConfigSpec.BooleanValue LOOT_SCALING;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NON_PLAYER_KILLS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Lore book drop settings").push("drops");

        GLOBAL_DROP_CHANCE = builder
                .comment("Base chance (0.0 to 1.0) for a lore book to drop when at least one matching book exists")
                .defineInRange("globalDropChance", 0.05, 0.0, 1.0);

        ONLY_HOSTILE_MOBS = builder
                .comment("If true, only hostile mobs (MobCategory.MONSTER) can drop lore books")
                .define("onlyHostileMobs", true);

        MAX_BOOKS_PER_KILL = builder
                .comment("Maximum number of different lore books that can drop from a single kill")
                .defineInRange("maxBooksPerKill", 1, 1, 10);

        ENABLE_PER_BOOK_WEIGHTS = builder
                .comment("If true, use each book definition's weight for selection. If false, all matching books have equal chance.")
                .define("enablePerBookWeights", true);

        LOOT_SCALING = builder
                .comment("If true, the Looting enchantment increases drop chance (+1% per level)")
                .define("lootScaling", false);

        ALLOW_NON_PLAYER_KILLS = builder
                .comment("If true, non-player kills (e.g. wolves, golems) can also trigger book drops")
                .define("allowNonPlayerKills", false);

        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
