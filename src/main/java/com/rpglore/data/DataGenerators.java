package com.rpglore.data;

import com.rpglore.RpgLoreMod;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wires data-providers into Forge's {@link GatherDataEvent}.
 * Invoked by {@code ./gradlew runData}; outputs land in
 * {@code src/generated/resources}, which {@code build.gradle} adds to the
 * main resource source set.
 */
@Mod.EventBusSubscriber(modid = RpgLoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DataGenerators {

    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();

        ModBlockTagsProvider blockTags =
                new ModBlockTagsProvider(output, event.getLookupProvider(), helper);
        generator.addProvider(event.includeServer(), blockTags);

        generator.addProvider(event.includeServer(),
                new ModItemTagsProvider(output, event.getLookupProvider(),
                        blockTags.contentsGetter(), helper));
    }
}
