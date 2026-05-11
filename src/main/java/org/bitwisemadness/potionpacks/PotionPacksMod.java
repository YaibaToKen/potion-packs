package org.bitwisemadness.potionpacks;

import org.bitwisemadness.potionpacks.common.events.ModCommonEvents;
import org.bitwisemadness.potionpacks.common.init.MainPotions;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PotionPacksMod.MOD_ID)
public class PotionPacksMod {
	public static final String MOD_ID = "potionpacks";

	public PotionPacksMod() {
		LogUtil.info(Env.COMMON, "Initializing Potion Packs...");
		LogUtil.info(Env.COMMON, "Starting potion pack discovery and parsing");
		PotionPackManager.initialize();
		LogUtil.info(Env.COMMON, "Potion pack discovery complete");
		var bus = FMLJavaModLoadingContext.get().getModEventBus();
		MainPotions.register(bus);
		bus.addListener(ModCommonEvents::commonSetup);
		LogUtil.info(Env.COMMON, "Potion Packs initialized");
	}
}
