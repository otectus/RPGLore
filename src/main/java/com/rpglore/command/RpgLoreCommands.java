package com.rpglore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rpglore.config.BooksConfigLoader;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
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

public final class RpgLoreCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BOOK_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    BooksConfigLoader.getAllBooks().stream()
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
                                                .executes(RpgLoreCommands::executeGive)
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2))
                                .executes(RpgLoreCommands::executeList)
                        )
        );
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        BooksConfigLoader.reload();
        int total = BooksConfigLoader.getAllBooks().size();
        ctx.getSource().sendSuccess(
                () -> Component.literal("RPG Lore: Reloaded " + total + " book definition(s)"),
                true);
        return 1;
    }

    private static int executeGive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String bookId = ResourceLocationArgument.getId(ctx, "book_id").toString();

        Optional<LoreBookDefinition> optDef = BooksConfigLoader.getById(bookId);
        if (optDef.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Unknown lore book: " + bookId));
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

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.0f);
            count++;
        }

        final int given = count;
        final String title = def.title();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Gave \"" + title + "\" to " + given + " player(s)"),
                true);
        return count;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        Collection<LoreBookDefinition> books = BooksConfigLoader.getAllBooks();
        if (books.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("RPG Lore: No book definitions loaded"),
                    false);
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("RPG Lore: " + books.size() + " book(s) loaded:"),
                false);
        for (LoreBookDefinition def : books) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("  - " + def.id() + " (\"" + def.title() + "\")"),
                    false);
        }
        return books.size();
    }

    private RpgLoreCommands() {}
}
