package com.rpglore.data;

import com.rpglore.RpgLoreMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * Empty block-tags provider. Currently exists solely so that
 * {@link ModItemTagsProvider} can chain off its content-getter (Forge's
 * ItemTagsProvider constructor requires a block-tag lookup future).
 * Add entries here if/when the mod registers its own blocks.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> lookupProvider,
                                ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, RpgLoreMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // intentionally empty
    }
}
