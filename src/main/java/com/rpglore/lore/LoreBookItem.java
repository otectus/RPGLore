package com.rpglore.lore;

import com.rpglore.config.ClientConfig;
import com.rpglore.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

public class LoreBookItem extends WrittenBookItem {

    private static final String TOOLTIP_SEPARATOR = "----------";

    public LoreBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("lore_show_glint")) {
            return tag.getBoolean("lore_show_glint");
        }
        return true; // default: glint enabled
    }

    /**
     * Override use() to bypass THREE hardcoded Items.WRITTEN_BOOK checks in vanilla:
     *   1. WrittenBookItem.use() → calls player.openItemGui() which checks Items.WRITTEN_BOOK
     *   2. ServerPlayer.openItemGui() → checks stack.is(Items.WRITTEN_BOOK)
     *   3. ClientPacketListener.handleOpenBook() → checks itemstack.is(Items.WRITTEN_BOOK)
     *
     * Solution: on the CLIENT side, open BookViewScreen directly via a @OnlyIn(CLIENT)
     * helper class (LoreBookClientHelper), invoked through DistExecutor for safety.
     * On the SERVER side, resolve text components and sync the stack.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();

        // Validate that the book has the required NBT
        if (tag == null || !tag.contains("pages", 9)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level.isClientSide) {
            // CLIENT: open the book screen directly, bypassing all vanilla item checks.
            // Uses DistExecutor + a @OnlyIn(CLIENT) helper class so this code is safe
            // on dedicated servers (the helper class is never loaded server-side).
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> LoreBookClientHelper.openBookScreen(stack));
        } else if (player instanceof ServerPlayer serverPlayer) {
            // SERVER: resolve any raw JSON text components in the pages
            WrittenBookItem.resolveBookComponents(stack, serverPlayer.createCommandSourceStack(), serverPlayer);
            serverPlayer.containerMenu.broadcastChanges();
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Override the item's display name to use the book title with custom styling.
     * This replaces the default white item name with our bold, colored title,
     * preventing the duplicate title that would occur if we added it in appendHoverText.
     */
    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("title")) {
            String title = tag.getString("title");
            if (!title.isEmpty()) {
                Style titleStyle = Style.EMPTY.withBold(true);
                if (tag.contains("lore_title_color")) {
                    titleStyle = titleStyle.withColor(parseNbtColor(tag.getString("lore_title_color"), ChatFormatting.YELLOW));
                } else {
                    titleStyle = titleStyle.withColor(ChatFormatting.YELLOW);
                }
                return Component.literal(title).withStyle(titleStyle);
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        // --- Author (bold, colored) ---
        String author = tag.getString("author");
        if (!author.isEmpty()) {
            Style authorStyle = Style.EMPTY.withBold(true);
            if (tag.contains("lore_author_color")) {
                authorStyle = authorStyle.withColor(parseNbtColor(tag.getString("lore_author_color"), ChatFormatting.GRAY));
            } else {
                authorStyle = authorStyle.withColor(ChatFormatting.GRAY);
            }
            tooltip.add(Component.translatable("book.byAuthor", author).withStyle(authorStyle));
        }

        // --- Generation (italic; gold for Original, dark gray for copies) ---
        if (!tag.getBoolean("lore_hide_generation")) {
            int generation = tag.getInt("generation");
            if (generation >= 0 && generation <= 3) {
                Component genLabel = Component.translatable("book.generation." + generation);
                if (generation == 0) {
                    tooltip.add(genLabel.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                } else {
                    tooltip.add(genLabel.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
            }
        }

        // --- Description (separator + italic, colored) ---
        if (tag.contains("lore_description")) {
            String description = tag.getString("lore_description");
            if (!description.isEmpty()) {
                tooltip.add(Component.literal(TOOLTIP_SEPARATOR)
                        .withStyle(ChatFormatting.DARK_GRAY));

                Style descStyle = Style.EMPTY.withItalic(true);
                if (tag.contains("lore_description_color")) {
                    descStyle = descStyle.withColor(parseNbtColor(tag.getString("lore_description_color"), ChatFormatting.AQUA));
                } else {
                    descStyle = descStyle.withColor(ChatFormatting.AQUA);
                }
                tooltip.add(Component.literal(description).withStyle(descStyle));
            }
        }

        // --- Lore ID (debug/pack-author feature) ---
        if (ClientConfig.SHOW_LORE_ID_IN_TOOLTIP.get()) {
            if (tag.contains("lore_id")) {
                tooltip.add(Component.empty());
                tooltip.add(Component.literal(tag.getString("lore_id"))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    private static TextColor parseNbtColor(String hex, ChatFormatting fallback) {
        try {
            return TextColor.fromRgb(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return TextColor.fromLegacyFormat(fallback);
        }
    }

    /**
     * Creates an ItemStack for the given lore book definition with proper NBT.
     */
    public static ItemStack createStack(LoreBookDefinition def) {
        ItemStack stack = new ItemStack(ModItems.LORE_BOOK.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putString("lore_id", def.id());
        tag.putString("title", def.title());
        tag.putString("author", def.author());
        tag.putInt("generation", def.generation());
        tag.putByte("resolved", (byte) 1);

        ListTag pagesList = new ListTag();
        for (String page : def.pages()) {
            pagesList.add(StringTag.valueOf(page));
        }
        tag.put("pages", pagesList);

        // Aesthetic NBT (only written when present)
        if (def.titleColor() != null) {
            tag.putString("lore_title_color", def.titleColor());
        }
        if (def.authorColor() != null) {
            tag.putString("lore_author_color", def.authorColor());
        }
        if (def.description() != null && !def.description().isEmpty()) {
            tag.putString("lore_description", def.description());
        }
        if (def.descriptionColor() != null) {
            tag.putString("lore_description_color", def.descriptionColor());
        }
        if (def.hideGeneration()) {
            tag.putBoolean("lore_hide_generation", true);
        }
        tag.putBoolean("lore_show_glint", def.showGlint());
        if (def.category() != null && !def.category().isEmpty()) {
            tag.putString("lore_category", def.category());
        }
        if (def.codexExclude()) {
            tag.putBoolean("lore_codex_exclude", true);
        }

        return stack;
    }
}
