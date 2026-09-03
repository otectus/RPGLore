package com.rpglore.lore;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the ForgeGradle test classpath carries the deobfuscated Minecraft jar,
 * so parser code may reference {@link ResourceLocation} directly.
 */
class ResourceLocationSmokeTest {

    @Test
    void resourceLocationParsesModIdNamespace() {
        ResourceLocation id = new ResourceLocation("rpg_lore:x");
        assertEquals("rpg_lore", id.getNamespace());
        assertEquals("x", id.getPath());
    }
}
