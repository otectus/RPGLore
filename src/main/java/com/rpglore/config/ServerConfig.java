package com.rpglore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    // --- Drop settings ---
    public static final ForgeConfigSpec.DoubleValue GLOBAL_DROP_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ONLY_HOSTILE_MOBS;
    public static final ForgeConfigSpec.IntValue MAX_BOOKS_PER_KILL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PER_BOOK_WEIGHTS;
    public static final ForgeConfigSpec.BooleanValue LOOT_SCALING;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NON_PLAYER_KILLS;

    // --- Codex settings ---
    public static final ForgeConfigSpec.BooleanValue CODEX_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CODEX_SOULBOUND;
    public static final ForgeConfigSpec.BooleanValue CODEX_AUTO_COLLECT;
    public static final ForgeConfigSpec.BooleanValue CODEX_GRANT_ON_FIRST_JOIN;
    public static final ForgeConfigSpec.BooleanValue CODEX_ALLOW_COPY;
    public static final ForgeConfigSpec.BooleanValue CODEX_ALLOW_DUPLICATE_PREVENTION;
    public static final ForgeConfigSpec.BooleanValue CODEX_REVEAL_UNCOLLECTED_NAMES;

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

        builder.comment("Lore Codex settings").push("codex");

        CODEX_ENABLED = builder
                .comment("Enable the Lore Codex feature entirely")
                .define("enabled", true);

        CODEX_SOULBOUND = builder
                .comment("If true, the Codex is kept on death and cannot be dropped/traded")
                .define("soulbound", true);

        CODEX_AUTO_COLLECT = builder
                .comment("If true, picking up a new Lore Book automatically registers it in the Codex")
                .define("autoCollect", true);

        CODEX_GRANT_ON_FIRST_JOIN = builder
                .comment("If true, players receive a Codex on first login")
                .define("grantOnFirstJoin", true);

        CODEX_ALLOW_COPY = builder
                .comment("If true, players can copy books from the Codex (consumes a physical copy)")
                .define("allowCopy", true);

        CODEX_ALLOW_DUPLICATE_PREVENTION = builder
                .comment("If true, the duplicate prevention toggle is available in the Codex UI")
                .define("allowDuplicatePrevention", true);

        CODEX_REVEAL_UNCOLLECTED_NAMES = builder
                .comment("If true, uncollected book names are shown in the Codex. If false, they appear as '???'")
                .define("revealUncollectedNames", false);

        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
