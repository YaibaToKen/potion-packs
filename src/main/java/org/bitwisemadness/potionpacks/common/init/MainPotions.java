package org.bitwisemadness.potionpacks.common.init;

import org.bitwisemadness.potionpacks.Env;
import org.bitwisemadness.potionpacks.LogUtil;
import org.bitwisemadness.potionpacks.Reference;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager.BrewingRecipeDefinition;
import org.bitwisemadness.potionpacks.common.packs.PotionPackManager.ResolvedPotionFamily;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MainPotions {
	public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, Reference.MOD_ID);
	private static final Map<String, RegistryObject<Potion>> POTION_VARIANTS = new LinkedHashMap<>();
	private static boolean variantsRegistered = false;

	private MainPotions() {}

	public static void register(IEventBus eventBus) {
		ensureVariantsRegistered();
		POTIONS.register(eventBus);
	}

	private static void ensureVariantsRegistered() {
		if (variantsRegistered) return;
		variantsRegistered = true;
		for (ResolvedPotionFamily family : PotionPackManager.getResolvedFamilies().values()) {
			if (!shouldRegisterFamily(family)) continue;
			for (PotionPackManager.ResolvedPotionVariant variant : family.getVariants()) {
				String variantKey = variant.variantKey();
				POTION_VARIANTS.put(variantKey, POTIONS.register(variant.getRegistryName(), () -> {
					MobEffect effect = family.resolveMobEffect();
					if (effect == null) {
						LogUtil.warn(Env.COMMON, "Skipping potion variant '{}' for family '{}' because effect '{}' is missing at registry time", variant.getRegistryName(), family.getDefinition().id, family.getEffectReference());
						return new Potion();
					}
					return new Potion(new MobEffectInstance(effect, variant.getDuration().getTicks(), variant.getTier().getAmplifier()));
				}));
			}
		}
	}

	private static boolean shouldRegisterFamily(ResolvedPotionFamily family) {
		if (family == null) return false;
		String namespace = family.getExpectedEffectNamespace();
		if (namespace == null) {
			LogUtil.warn(Env.COMMON, "Skipping potion family '{}' because it has no resolvable effect reference", family.getDefinition().id);
			return false;
		}
		if (!"minecraft".equals(namespace) && !Reference.MOD_ID.equals(namespace) && !ModList.get().isLoaded(namespace)) {
			LogUtil.warn(Env.COMMON, "Skipping potion family '{}' because effect namespace '{}' is not loaded", family.getDefinition().id, namespace);
			return false;
		}
		if (family.resolveMobEffect() == null && "minecraft".equals(namespace)) {
			LogUtil.warn(Env.COMMON, "Skipping potion family '{}' because vanilla effect '{}' does not exist", family.getDefinition().id, family.getEffectReference());
			return false;
		}
		return true;
	}

	public static RegistryObject<Potion> getPotionVariant(String familyId, String tierId, String durationId) {
		return POTION_VARIANTS.get(familyId.toLowerCase(Locale.ROOT) + ":" + tierId.toLowerCase(Locale.ROOT) + ":" + durationId.toLowerCase(Locale.ROOT));
	}

	public static void registerBrewingRecipes() {
		for (ResolvedPotionFamily family : PotionPackManager.getResolvedFamilies().values()) {
			if (!shouldRegisterFamily(family)) continue;
			for (BrewingRecipeDefinition recipe : family.getDefinition().brewing) {
				if (recipe == null || Boolean.FALSE.equals(recipe.enabled) || recipe.ingredient == null) continue;
				Potion inputPotion = resolvePotionReference(family, recipe.inputPotion, recipe.inputTier, recipe.inputDuration);
				Potion outputPotion = resolvePotionReference(family, recipe.outputPotion, recipe.outputTier, recipe.outputDuration);
				Item ingredientItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(recipe.ingredient));
				if (inputPotion == null || outputPotion == null || ingredientItem == null) continue;
				addPotionMix(inputPotion, ingredientItem, outputPotion);
			}
		}
	}

	private static void addPotionMix(Potion inputPotion, Item ingredientItem, Potion outputPotion) {
		BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
			Ingredient.of(PotionUtils.setPotion(new ItemStack(Items.POTION), inputPotion)),
			Ingredient.of(ingredientItem),
			PotionUtils.setPotion(new ItemStack(Items.POTION), outputPotion)
		));
	}

	private static Potion resolvePotionReference(ResolvedPotionFamily family, String directPotionId, String tierId, String durationId) {
		if (directPotionId != null && !directPotionId.isBlank()) {
			return ForgeRegistries.POTIONS.getValue(ResourceLocation.tryParse(directPotionId));
		}
		if (tierId == null || durationId == null) return null;
		RegistryObject<Potion> registryObject = getPotionVariant(family.getDefinition().id, tierId, durationId);
		return registryObject != null ? registryObject.get() : null;
	}
}
