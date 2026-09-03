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

    // --- Acquisition settings ---
    public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_DROPS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOOT_TABLES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ADVANCEMENTS;

    // --- Codex settings ---
    public static final ForgeConfigSpec.BooleanValue CODEX_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CODEX_SOULBOUND;
    public static final ForgeConfigSpec.BooleanValue CODEX_AUTO_COLLECT;
    public static final ForgeConfigSpec.BooleanValue CODEX_GRANT_ON_FIRST_JOIN;
    public static final ForgeConfigSpec.BooleanValue CODEX_ALLOW_COPY;
    public static final ForgeConfigSpec.BooleanValue CODEX_ALLOW_DUPLICATE_PREVENTION;
    public static final ForgeConfigSpec.BooleanValue CODEX_REVEAL_UNCOLLECTED_NAMES;
    public static final ForgeConfigSpec.BooleanValue CODEX_ENABLE_DISCOVERY_HINTS;

    // --- Reader settings ---
    public static final ForgeConfigSpec.BooleanValue READER_ALLOW_RUN_COMMAND_CLICKS;

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

        builder.comment("Which acquisition sources are active").push("acquisition");

        ENABLE_ENTITY_DROPS = builder
                .comment("If true, books with an entity_drop rule can drop from mob kills")
                .define("enableEntityDrops", true);

        ENABLE_LOOT_TABLES = builder
                .comment("If true, books with a loot_table rule are injected into the named loot tables",
                        "(chests, fishing, gameplay tables). Entity tables are never touched here.")
                .define("enableLootTables", true);

        ENABLE_ADVANCEMENTS = builder
                .comment("If true, books with an advancement rule are granted when a player earns",
                        "one of the listed advancements")
                .define("enableAdvancements", true);

        builder.pop();

        builder.comment("Lore Codex settings").push("codex");

        CODEX_ENABLED = builder
                .comment("Enable the Lore Codex feature entirely")
                .define("enabled", true);

        CODEX_SOULBOUND = builder
                .comment("If true, the Codex survives death: it is stashed away on death (from the",
                        "inventory or a Curios slot) and restored on respawn, or on the next login",
                        "if the player disconnects first. Has no effect when keepInventory is on,",
                        "since the Codex is never dropped in that case.")
                .define("soulbound", true);

        CODEX_AUTO_COLLECT = builder
                .comment("If true, picking up a new Lore Book automatically registers it in the Codex")
                .define("autoCollect", true);

        CODEX_GRANT_ON_FIRST_JOIN = builder
                .comment("If true, players receive a Codex on first login")
                .define("grantOnFirstJoin", true);

        CODEX_ALLOW_COPY = builder
                .comment("Allow players to extract a banked spare copy from the Codex as a physical",
                        "book. The Codex's permanent entry is never consumed.")
                .define("allowCopy", true);

        CODEX_ALLOW_DUPLICATE_PREVENTION = builder
                .comment("Allow players to choose duplicate handling: store duplicate pickups as",
                        "spare copies (default) or leave duplicates on the ground.")
                .define("allowDuplicatePrevention", true);

        CODEX_REVEAL_UNCOLLECTED_NAMES = builder
                .comment("If true, uncollected book names are shown in the Codex. If false, they appear as '???'")
                .define("revealUncollectedNames", false);

        CODEX_ENABLE_DISCOVERY_HINTS = builder
                .comment("If true, uncollected entries may show the discovery hint from their book definition")
                .define("enableDiscoveryHints", true);

        builder.pop();

        builder.comment("Lore book reader settings").push("reader");

        READER_ALLOW_RUN_COMMAND_CLICKS = builder
                .comment("Allow run_command click events inside lore book text. Off by default",
                        "because lore packs are third-party content.")
                .define("allowRunCommandClicks", false);

        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
