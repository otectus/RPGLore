package com.rpglore.registry;

import com.rpglore.RpgLoreMod;
import com.rpglore.lore.LoreBookItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RpgLoreMod.MODID);

    public static final RegistryObject<LoreBookItem> LORE_BOOK =
            ITEMS.register("lore_book", () -> new LoreBookItem(new Item.Properties().stacksTo(1)));
}
