package com.rpglore.loot;

import com.mojang.serialization.Codec;
import com.rpglore.RpgLoreMod;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, RpgLoreMod.MODID);

    public static final RegistryObject<Codec<LoreBookLootModifier>> LORE_BOOK_DROPS =
            LOOT_MODIFIER_SERIALIZERS.register("lore_drops", LoreBookLootModifier.CODEC);
}
