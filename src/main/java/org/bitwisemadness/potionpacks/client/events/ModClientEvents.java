package org.bitwisemadness.potionpacks.client.events;

import org.bitwisemadness.potionpacks.PotionPacksMod;
import org.bitwisemadness.potionpacks.client.resources.PotionLangPackResources;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PotionPacksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModClientEvents {
	private ModClientEvents() {}

	@SubscribeEvent
	public static void onAddPackFinders(AddPackFindersEvent event) {
		if (event.getPackType() != PackType.CLIENT_RESOURCES || !PotionPackManager.hasLoadedPotionFamilies()) {
			return;
		}

		event.addRepositorySource((packConsumer) -> {
			Pack pack = Pack.readMetaAndCreate(
				"potionpacks_generated_translations",
				Component.literal("Potion Packs Generated Translations"),
				true,
				PotionLangPackResources::new,
				PackType.CLIENT_RESOURCES,
				Pack.Position.TOP,
				PackSource.BUILT_IN
			);
			if (pack != null) packConsumer.accept(pack);
		});
	}
}
