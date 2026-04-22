package org.bitwisemadness.potionpacks.common.events;

import org.bitwisemadness.potionpacks.common.init.MainPotions;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public final class ModCommonEvents {
	private ModCommonEvents() {}

	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(MainPotions::registerBrewingRecipes);
	}
}
