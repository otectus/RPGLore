package com.rpglore.data;

import com.rpglore.RpgLoreMod;
import com.rpglore.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * Generates item tag JSONs into {@code src/generated/resources}.
 * Run via {@code ./gradlew runData}.
 *
 * <p>Currently emits the vanilla {@code minecraft:bookshelf_books} tag so
 * LoreBookItem and LoreCodexItem can be stored in chiseled bookshelves
 * alongside regular books.
 */
public class ModItemTagsProvider extends ItemTagsProvider {

    public ModItemTagsProvider(PackOutput output,
                               CompletableFuture<HolderLookup.Provider> lookupProvider,
                               CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
                               ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, RpgLoreMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ItemTags.BOOKSHELF_BOOKS)
                .add(ModItems.LORE_BOOK.get())
                .add(ModItems.LORE_CODEX.get());
    }
}
