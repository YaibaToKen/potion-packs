package org.bitwisemadness.potionpacks;

import org.bitwisemadness.potionpacks.common.events.ModCommonEvents;
import org.bitwisemadness.potionpacks.common.init.MainPotions;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Reference.MOD_ID)
public class PotionPacksMod {
	public PotionPacksMod() {
		LogUtil.info(Env.COMMON, "Initializing Potion Packs...");
		PotionPackManager.initialize();
		var bus = FMLJavaModLoadingContext.get().getModEventBus();
		MainPotions.register(bus);
		bus.addListener(ModCommonEvents::commonSetup);
		LogUtil.info(Env.COMMON, "Potion Packs initialized");
	}
}
