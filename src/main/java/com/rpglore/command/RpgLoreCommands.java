package com.rpglore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rpglore.codex.CodexService;
import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.BooksConfigLoader;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import com.rpglore.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public final class RpgLoreCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOOK_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    LoreBookRegistry.getAllBooks().stream()
                            .map(LoreBookDefinition::id),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rpglore")
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(RpgLoreCommands::executeReload)
                        )
                        .then(Commands.literal("give")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("book_id", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_BOOK_IDS)
                                                // H4: Default to not tracking admin gives
                                                .executes(ctx -> executeGive(ctx, false))
                                                .then(Commands.argument("track", BoolArgumentType.bool())
                                                        .executes(ctx -> executeGive(ctx, BoolArgumentType.getBool(ctx, "track")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2))
                                .executes(RpgLoreCommands::executeList)
                        )
                        // E5: Self-service collection command (no permission required)
                        .then(Commands.literal("collection")
                                .executes(RpgLoreCommands::executeCollection)
                        )
                        // Codex admin commands
                        .then(Commands.literal("codex")
                                .then(Commands.literal("reset")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(RpgLoreCommands::executeCodexReset)
                                        )
                                )
                                .then(Commands.literal("add")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("book_id", ResourceLocationArgument.id())
                                                        .suggests(SUGGEST_BOOK_IDS)
                                                        .executes(RpgLoreCommands::executeCodexAdd)
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("book_id", ResourceLocationArgument.id())
                                                        .suggests(SUGGEST_BOOK_IDS)
                                                        .executes(RpgLoreCommands::executeCodexRemove)
                                                )
                                        )
                                )
                                .then(Commands.literal("give")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(RpgLoreCommands::executeCodexGive)
                                        )
                                )
                                .then(Commands.literal("status")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(RpgLoreCommands::executeCodexStatus)
                                        )
                                )
                        )
        );
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        BooksConfigLoader.reload();
        int total = LoreBookRegistry.getBookCount();

        // Prune stale entries and resync all online players
        CodexService service = CodexService.get();
        if (service != null && ctx.getSource().getServer() != null) {
            service.pruneAndResync(ctx.getSource().getServer());
        }

        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.reload.success", total),
                true);
        return 1;
    }

    private static int executeGive(CommandContext<CommandSourceStack> ctx, boolean track) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String bookId = ResourceLocationArgument.getId(ctx, "book_id").toString();

        Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
        if (optDef.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.unknown_book", bookId));
            return 0;
        }

        LoreBookDefinition def = optDef.get();
        int count = 0;

        for (ServerPlayer player : targets) {
            ItemStack stack = LoreBookItem.createStack(def);

            // Try to add to inventory; if full, drop on ground
            if (!player.getInventory().add(stack)) {
                ItemEntity drop = player.drop(stack, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setThrower(player.getUUID());
                }
            }

            // H4: Only record for per-player copy tracking if explicitly requested
            if (track) {
                LoreBookRegistry.recordPlayerReceived(player.getUUID(), def.id());
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
            count++;
        }

        final int given = count;
        final String title = def.title();
        final boolean tracked = track;
        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        tracked ? "rpg_lore.command.give.success.tracked"
                                : "rpg_lore.command.give.success.untracked",
                        title, given),
                true);
        return count;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        Collection<LoreBookDefinition> books = LoreBookRegistry.getAllBooks();
        if (books.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("rpg_lore.command.list.none"),
                    false);
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.list.header", books.size()),
                false);
        for (LoreBookDefinition def : books) {
            Component category = def.category() != null
                    ? Component.translatable("rpg_lore.command.list.category", def.category())
                    : Component.empty();
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("rpg_lore.command.list.entry",
                            def.id(), def.title(), category),
                    false);
        }
        return books.size();
    }

    // E5: Self-service collection view (permission level 0)
    private static int executeCollection(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.players_only"));
            return 0;
        }

        CodexTrackingData codexData = CodexTrackingData.getInstance();
        if (codexData == null) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.codex.unavailable"));
            return 0;
        }

        Set<String> collected = codexData.getCollectedBooks(player.getUUID());
        Set<String> eligibleIds = LoreBookRegistry.getCodexEligibleIds();

        // Only count collected books that are still eligible
        int eligibleCollected = 0;
        for (String id : collected) {
            if (eligibleIds.contains(id)) eligibleCollected++;
        }
        int totalEligible = eligibleIds.size();
        final int displayCount = eligibleCollected;

        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.collection.header",
                        displayCount, totalEligible),
                false);

        if (!collected.isEmpty()) {
            for (String bookId : collected) {
                if (!eligibleIds.contains(bookId)) continue;
                Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
                String title = optDef.map(LoreBookDefinition::title)
                        .orElseGet(() -> Component.translatable("rpg_lore.command.unknown_title").getString());
                ctx.getSource().sendSuccess(
                        () -> Component.translatable("rpg_lore.command.collection.entry", title),
                        false);
            }
        }
        return displayCount;
    }

    // --- Codex admin commands ---

    private static int executeCodexReset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        CodexService service = CodexService.get();
        if (service == null) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.codex.unavailable"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            service.resetPlayer(player);
            count++;
        }
        final int total = count;
        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.codex.reset.success", total),
                true);
        return count;
    }

    private static int executeCodexAdd(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String bookId = ResourceLocationArgument.getId(ctx, "book_id").toString();

        Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
        if (optDef.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.unknown_book", bookId));
            return 0;
        }

        CodexService service = CodexService.get();
        if (service == null) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.codex.unavailable"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            service.collectBook(player, bookId);
            count++;
        }
        final int total = count;
        final String title = optDef.get().title();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.codex.add.success", title, total),
                true);
        return count;
    }

    private static int executeCodexRemove(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String bookId = ResourceLocationArgument.getId(ctx, "book_id").toString();

        CodexService service = CodexService.get();
        if (service == null) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.codex.unavailable"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            service.removeBook(player, bookId);
            count++;
        }
        final int total = count;
        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.codex.remove.success", total),
                true);
        return count;
    }

    private static int executeCodexGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int count = 0;

        for (ServerPlayer player : targets) {
            ItemStack codex = new ItemStack(ModItems.LORE_CODEX.get());

            if (!player.getInventory().add(codex)) {
                ItemEntity drop = player.drop(codex, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.setThrower(player.getUUID());
                }
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
            count++;
        }

        final int total = count;
        ctx.getSource().sendSuccess(
                () -> Component.translatable("rpg_lore.command.codex.give.success", total),
                true);
        return count;
    }

    private static int executeCodexStatus(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        CodexTrackingData codexData = CodexTrackingData.getInstance();
        if (codexData == null) {
            ctx.getSource().sendFailure(Component.translatable("rpg_lore.command.codex.unavailable"));
            return 0;
        }

        Set<String> collected = codexData.getCollectedBooks(target.getUUID());
        Set<String> eligibleIds = LoreBookRegistry.getCodexEligibleIds();
        boolean preventDuplicates = codexData.isPreventDuplicates(target.getUUID());

        int eligibleCollected = 0;
        for (String id : collected) {
            if (eligibleIds.contains(id)) eligibleCollected++;
        }
        int totalEligible = eligibleIds.size();
        final int displayCount = eligibleCollected;
        final String targetName = target.getName().getString();
        final String headerKey = preventDuplicates
                ? "rpg_lore.command.codex.status.header.blocking"
                : "rpg_lore.command.codex.status.header";

        ctx.getSource().sendSuccess(
                () -> Component.translatable(headerKey, targetName, displayCount, totalEligible),
                false);

        for (String bookId : collected) {
            if (!eligibleIds.contains(bookId)) continue;
            Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
            String title = optDef.map(LoreBookDefinition::title)
                    .orElseGet(() -> Component.translatable("rpg_lore.command.unknown_title").getString());
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("rpg_lore.command.codex.status.entry", title, bookId),
                    false);
        }
        return displayCount;
    }

    private RpgLoreCommands() {}
}
