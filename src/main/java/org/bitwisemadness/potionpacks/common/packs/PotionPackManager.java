package org.bitwisemadness.potionpacks.common.packs;

import org.bitwisemadness.potionpacks.Env;
import org.bitwisemadness.potionpacks.LogUtil;
import org.bitwisemadness.potionpacks.Reference;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.effect.MobEffect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PotionPackManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();
	private static final String PACK_RELATIVE_ROOT = "data/dragonminez/potions/";
	private static final Path INSTANCE_PACK_DIR = FMLPaths.GAMEDIR.get().resolve("potionpacks");

	@Getter
	private static final Map<String, ResolvedPotionFamily> resolvedFamilies = new LinkedHashMap<>();
		private static final Map<String, List<String>> EFFECT_TAG_ALIASES = createEffectTagAliases();
	private static boolean initialized = false;

	private PotionPackManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		resolvedFamilies.clear();

		Map<String, PotionFamilyDefinition> mergedFamilies = new LinkedHashMap<>();
		loadExternalDefinitions(mergedFamilies);
		resolveFamilies(mergedFamilies);
		initialized = true;

		LogUtil.info(Env.COMMON, "Loaded {} potion family definitions", resolvedFamilies.size());
	}

	public static boolean hasLoadedPotionFamilies() {
		return !resolvedFamilies.isEmpty();
	}

	public static Map<String, String> buildGeneratedPotionTranslations() {
		Map<String, String> translations = new LinkedHashMap<>();
		for (ResolvedPotionFamily family : resolvedFamilies.values()) {
			String familyName = family.getDisplayName();
			for (ResolvedPotionVariant variant : family.getVariants()) {
				String variantName = variant.getRegistryName();
				translations.put("item.minecraft.potion.effect." + variantName, familyName);
				translations.put("item.minecraft.splash_potion.effect." + variantName, "Splash " + familyName);
				translations.put("item.minecraft.lingering_potion.effect." + variantName, "Lingering " + familyName);
				translations.put("item.minecraft.tipped_arrow.effect." + variantName, "Arrow of " + familyName);
			}
		}
		return translations;
	}

	public static byte[] buildGeneratedPotionTranslationsJsonBytes() {
		return GSON.toJson(buildGeneratedPotionTranslations()).getBytes(StandardCharsets.UTF_8);
	}

	private static void loadExternalDefinitions(Map<String, PotionFamilyDefinition> mergedFamilies) {
		try {
			Files.createDirectories(INSTANCE_PACK_DIR);
			List<Path> folderPacks = new ArrayList<>();
			List<Path> zipPacks = new ArrayList<>();

			try (var stream = Files.list(INSTANCE_PACK_DIR)) {
				stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).forEach(path -> {
					String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (Files.isDirectory(path)) {
						folderPacks.add(path);
					} else if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
						zipPacks.add(path);
					}
				});
			}

			for (Path packDir : folderPacks) {
				loadFolderPack(packDir, mergedFamilies);
			}
			for (Path zipPack : zipPacks) {
				loadZipPack(zipPack, mergedFamilies);
			}
		} catch (IOException e) {
			LogUtil.error(Env.COMMON, "Failed scanning potion pack directory '{}': {}", INSTANCE_PACK_DIR, e.getMessage());
		}
	}

	private static void loadFolderPack(Path packDir, Map<String, PotionFamilyDefinition> mergedFamilies) {
		Path potionRoot = packDir.resolve(PACK_RELATIVE_ROOT);
		if (!Files.isDirectory(potionRoot)) {
			return;
		}

		try (var walk = Files.walk(potionRoot)) {
			walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"))
				.sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
				.forEach(path -> {
					try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
						PotionFamilyDefinition family = GSON.fromJson(reader, PotionFamilyDefinition.class);
						mergeDefinition(mergedFamilies, family, "folder:" + packDir.getFileName() + "/" + potionRoot.relativize(path));
					} catch (Exception e) {
						LogUtil.error(Env.COMMON, "Failed to parse potion definition '{}': {}", path, e.getMessage());
					}
				});
		} catch (IOException e) {
			LogUtil.error(Env.COMMON, "Failed reading potion pack folder '{}': {}", packDir, e.getMessage());
		}
	}

	private static void loadZipPack(Path zipPath, Map<String, PotionFamilyDefinition> mergedFamilies) {
		try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
			List<? extends ZipEntry> entries = Collections.list(zipFile.entries());
			entries.stream()
				.filter(entry -> !entry.isDirectory() && entry.getName().startsWith(PACK_RELATIVE_ROOT) && entry.getName().endsWith(".json"))
				.sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
				.forEach(entry -> {
					try (InputStream inputStream = zipFile.getInputStream(entry)) {
						PotionFamilyDefinition family = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), PotionFamilyDefinition.class);
						mergeDefinition(mergedFamilies, family, "zip:" + zipPath.getFileName() + "!/" + entry.getName());
					} catch (Exception e) {
						LogUtil.error(Env.COMMON, "Failed to parse potion definition '{}' in '{}': {}", entry.getName(), zipPath.getFileName(), e.getMessage());
					}
				});
		} catch (IOException e) {
			LogUtil.error(Env.COMMON, "Failed reading potion pack zip '{}': {}", zipPath, e.getMessage());
		}
	}

	private static void mergeDefinition(Map<String, PotionFamilyDefinition> mergedFamilies, PotionFamilyDefinition incoming, String source) {
		if (incoming == null || incoming.id == null || incoming.id.isBlank()) {
			LogUtil.warn(Env.COMMON, "Skipping potion family from '{}' because it has no id", source);
			return;
		}

		if (!matchesLoadRequirements(incoming)) {
			LogUtil.info(Env.COMMON, "Skipping potion family override '{}' from '{}' because its mod conditions were not met", incoming.id, source);
			return;
		}

		String familyId = normalizeId(incoming.id);
		incoming.id = familyId;
		PotionFamilyDefinition existing = mergedFamilies.get(familyId);
		if (existing == null || Boolean.TRUE.equals(incoming.replace)) {
			mergedFamilies.put(familyId, incoming.copy());
			return;
		}

		existing.mergeFrom(incoming);
	}

	private static void resolveFamilies(Map<String, PotionFamilyDefinition> mergedFamilies) {
		for (PotionFamilyDefinition family : mergedFamilies.values()) {
			if (family == null || family.id == null) {
				continue;
			}
			if (Boolean.FALSE.equals(family.enabled)) {
				continue;
			}
			if (!matchesLoadRequirements(family)) {
				continue;
			}

			String effectReference = resolveEffectReference(family);
			ResourceLocation effectId = parseEffectReference(effectReference);
			if (effectReference == null || effectReference.isBlank()) {
				LogUtil.warn(Env.COMMON, "Potion family '{}' has no resolvable mob effect reference, skipping", family.id);
				continue;
			}
			if (effectId != null) {
				if (!canReferenceEffectNamespace(effectId)) {
					LogUtil.warn(Env.COMMON, "Potion family '{}' references effect '{}' from an unavailable namespace, skipping", family.id, effectId);
					continue;
				}
				if (!ForgeRegistries.MOB_EFFECTS.containsKey(effectId)) {
					if ("minecraft".equals(effectId.getNamespace())) {
						LogUtil.warn(Env.COMMON, "Potion family '{}' references missing vanilla effect '{}', skipping", family.id, effectId);
						continue;
					}
					LogUtil.info(Env.COMMON, "Potion family '{}' queued unresolved effect '{}' until registry access", family.id, effectId);
				}
			} else {
				LogUtil.info(Env.COMMON, "Potion family '{}' queued unresolved effect tag '{}' until registry access", family.id, effectReference);
			}

			Map<String, TierDefinition> enabledTiers = new LinkedHashMap<>();
			for (TierDefinition tier : family.tiers) {
				if (tier == null || tier.id == null) {
					continue;
				}
				tier.id = normalizeId(tier.id);
				if (Boolean.FALSE.equals(tier.enabled)) {
					continue;
				}
				enabledTiers.put(tier.id, tier.copy());
			}

			Map<String, DurationDefinition> enabledDurations = new LinkedHashMap<>();
			for (DurationDefinition duration : family.durations) {
				if (duration == null || duration.id == null) {
					continue;
				}
				duration.id = normalizeId(duration.id);
				if (Boolean.FALSE.equals(duration.enabled)) {
					continue;
				}
				enabledDurations.put(duration.id, duration.copy());
			}

			if (enabledTiers.isEmpty() || enabledDurations.isEmpty()) {
				continue;
			}

			List<ResolvedPotionVariant> resolvedVariants = new ArrayList<>();
			for (VariantDefinition variant : family.variants) {
				if (variant == null || variant.tier == null || variant.duration == null) {
					continue;
				}
				String tierId = normalizeId(variant.tier);
				String durationId = normalizeId(variant.duration);
				if (Boolean.FALSE.equals(variant.enabled)) {
					continue;
				}
				TierDefinition tier = enabledTiers.get(tierId);
				DurationDefinition duration = enabledDurations.get(durationId);
				if (tier == null || duration == null) {
					continue;
				}
				String registryName = sanitizeRegistryPath(family.id + "_" + tierId + "_" + durationId);
				resolvedVariants.add(new ResolvedPotionVariant(family.id, registryName, tier, duration, variant.copy()));
			}

			if (resolvedVariants.isEmpty()) {
				continue;
			}

			resolvedFamilies.put(family.id, new ResolvedPotionFamily(family.copy(), effectReference, effectId, enabledTiers, enabledDurations, resolvedVariants));
		}
	}



	private static boolean matchesLoadRequirements(PotionFamilyDefinition family) {
		if (family == null) {
			return false;
		}
		if (family.requiredMods != null && family.requiredMods.stream().anyMatch(modId -> modId != null && !modId.isBlank() && !ModList.get().isLoaded(modId))) {
			return false;
		}
		if (family.loadCondition == null || family.loadCondition.type == null || family.loadCondition.type.isBlank()) {
			return true;
		}
		return family.loadCondition.matches();
	}

	private static String resolveEffectReference(PotionFamilyDefinition family) {
		if (family == null) {
			return null;
		}
		if (family.mobEffect != null && !family.mobEffect.isBlank()) {
			return family.mobEffect.trim().toLowerCase(Locale.ROOT);
		}
		if (family.effectTags != null) {
			for (String tag : family.effectTags) {
				String resolved = resolveEffectTag(tag);
				if (resolved != null) {
					return resolved;
				}
			}
		}
		return null;
	}

	private static String resolveEffectTag(String rawTag) {
		if (rawTag == null || rawTag.isBlank()) {
			return null;
		}
		String tag = normalizeTag(rawTag);
		List<String> aliases = EFFECT_TAG_ALIASES.get(tag);
		if (aliases == null || aliases.isEmpty()) {
			return null;
		}
		for (String candidate : aliases) {
			ResourceLocation id = parseId(candidate);
			if (id == null) {
				continue;
			}
			if (!canReferenceEffectNamespace(id)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static String normalizeTag(String tag) {
		String normalized = tag.trim().toLowerCase(Locale.ROOT);
		return normalized.startsWith("#") ? normalized.substring(1) : normalized;
	}

	private static ResourceLocation parseEffectReference(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return raw.startsWith("#") ? parseId(resolveEffectTag(raw)) : parseId(raw);
	}

	private static Map<String, List<String>> createEffectTagAliases() {
		Map<String, List<String>> aliases = new HashMap<>();
		aliases.put("dragonminez:ki_regen_family", List.of("dragonminez:ki_regen"));
		aliases.put("dragonminez:stamina_regen_family", List.of("dragonminez:stamina_regen"));
		aliases.put("dragonminez:tp_gain_family", List.of("dragonminez:tp_gain"));
		aliases.put("dragonminez:mastery_gain_family", List.of("dragonminez:mastery_gain"));
		aliases.put("minecraft:speed_family", List.of("minecraft:speed"));
		aliases.put("minecraft:regeneration_family", List.of("minecraft:regeneration"));
		aliases.put("minecraft:jump_family", List.of("minecraft:jump_boost"));
		aliases.put("modded:mana_regen_family", List.of("ars_nouveau:mana_regen", "minecraft:regeneration"));
		return aliases;
	}

	private static String normalizeId(String id) {
		return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
	}

	private static String sanitizeRegistryPath(String value) {
		return normalizeId(value).replace(':', '_').replace('/', '_').replace('-', '_').replace(' ', '_');
	}

	private static ResourceLocation parseId(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
	}
	private static boolean canReferenceEffectNamespace(ResourceLocation effectId) {
		if (effectId == null) {
			return false;
		}
		String namespace = effectId.getNamespace();
		return "minecraft".equals(namespace) || Reference.MOD_ID.equals(namespace) || ModList.get().isLoaded(namespace);
	}


	public static class PotionFamilyDefinition {
		@SerializedName(value = "replace")
		public Boolean replace = false;
		public String id;
		public Boolean enabled = true;
		@SerializedName(value = "display_name", alternate = {"displayName"})
		public String displayName;
		@SerializedName(value = "mob_effect", alternate = {"mobEffect", "effect"})
		public String mobEffect;
		@SerializedName(value = "required_mods", alternate = {"requiredMods"})
		public List<String> requiredMods = new ArrayList<>();
		@SerializedName(value = "load_condition", alternate = {"loadCondition"})
		public LoadConditionDefinition loadCondition;
		@SerializedName(value = "effect_tags", alternate = {"effectTags", "mob_effect_tags", "mobEffectTags"})
		public List<String> effectTags = new ArrayList<>();
		public List<TierDefinition> tiers = new ArrayList<>();
		public List<DurationDefinition> durations = new ArrayList<>();
		public List<VariantDefinition> variants = new ArrayList<>();
		public List<BrewingRecipeDefinition> brewing = new ArrayList<>();

		public PotionFamilyDefinition copy() {
			PotionFamilyDefinition copy = new PotionFamilyDefinition();
			copy.replace = this.replace;
			copy.id = this.id;
			copy.enabled = this.enabled;
			copy.displayName = this.displayName;
			copy.mobEffect = this.mobEffect;
			copy.requiredMods = this.requiredMods == null ? new ArrayList<>() : new ArrayList<>(this.requiredMods);
			copy.loadCondition = this.loadCondition == null ? null : this.loadCondition.copy();
			copy.effectTags = this.effectTags == null ? new ArrayList<>() : new ArrayList<>(this.effectTags);
			for (TierDefinition tier : this.tiers) {
				copy.tiers.add(tier.copy());
			}
			for (DurationDefinition duration : this.durations) {
				copy.durations.add(duration.copy());
			}
			for (VariantDefinition variant : this.variants) {
				copy.variants.add(variant.copy());
			}
			for (BrewingRecipeDefinition recipe : this.brewing) {
				copy.brewing.add(recipe.copy());
			}
			return copy;
		}

		public void mergeFrom(PotionFamilyDefinition incoming) {
			if (incoming.enabled != null) this.enabled = incoming.enabled;
			if (incoming.displayName != null) this.displayName = incoming.displayName;
			if (incoming.mobEffect != null) this.mobEffect = incoming.mobEffect;
			if (incoming.requiredMods != null && !incoming.requiredMods.isEmpty()) this.requiredMods = new ArrayList<>(incoming.requiredMods);
			if (incoming.loadCondition != null) this.loadCondition = incoming.loadCondition.copy();
			if (incoming.effectTags != null && !incoming.effectTags.isEmpty()) this.effectTags = new ArrayList<>(incoming.effectTags);
			mergeTiers(incoming.tiers);
			mergeDurations(incoming.durations);
			mergeVariants(incoming.variants);
			mergeBrewing(incoming.brewing);
		}

		private void mergeTiers(List<TierDefinition> incomingTiers) {
			if (incomingTiers == null) {
				return;
			}
			Map<String, TierDefinition> merged = new LinkedHashMap<>();
			for (TierDefinition tier : this.tiers) {
				if (tier != null && tier.id != null) {
					merged.put(normalizeId(tier.id), tier.copy());
				}
			}
			for (TierDefinition tier : incomingTiers) {
				if (tier == null || tier.id == null) {
					continue;
				}
				String key = normalizeId(tier.id);
				TierDefinition existing = merged.get(key);
				if (existing == null) {
					merged.put(key, tier.copy());
				} else {
					existing.mergeFrom(tier);
				}
			}
			this.tiers = new ArrayList<>(merged.values());
		}

		private void mergeDurations(List<DurationDefinition> incomingDurations) {
			if (incomingDurations == null) {
				return;
			}
			Map<String, DurationDefinition> merged = new LinkedHashMap<>();
			for (DurationDefinition duration : this.durations) {
				if (duration != null && duration.id != null) {
					merged.put(normalizeId(duration.id), duration.copy());
				}
			}
			for (DurationDefinition duration : incomingDurations) {
				if (duration == null || duration.id == null) {
					continue;
				}
				String key = normalizeId(duration.id);
				DurationDefinition existing = merged.get(key);
				if (existing == null) {
					merged.put(key, duration.copy());
				} else {
					existing.mergeFrom(duration);
				}
			}
			this.durations = new ArrayList<>(merged.values());
		}

		private void mergeVariants(List<VariantDefinition> incomingVariants) {
			if (incomingVariants == null) {
				return;
			}
			Map<String, VariantDefinition> merged = new LinkedHashMap<>();
			for (VariantDefinition variant : this.variants) {
				if (variant != null && variant.tier != null && variant.duration != null) {
					merged.put(variant.key(), variant.copy());
				}
			}
			for (VariantDefinition variant : incomingVariants) {
				if (variant == null || variant.tier == null || variant.duration == null) {
					continue;
				}
				VariantDefinition existing = merged.get(variant.key());
				if (existing == null) {
					merged.put(variant.key(), variant.copy());
				} else {
					existing.mergeFrom(variant);
				}
			}
			this.variants = new ArrayList<>(merged.values());
		}

		private void mergeBrewing(List<BrewingRecipeDefinition> incomingRecipes) {
			if (incomingRecipes == null) {
				return;
			}
			Map<String, BrewingRecipeDefinition> merged = new LinkedHashMap<>();
			for (BrewingRecipeDefinition recipe : this.brewing) {
				if (recipe != null) {
					merged.put(recipe.key(), recipe.copy());
				}
			}
			for (BrewingRecipeDefinition recipe : incomingRecipes) {
				if (recipe != null) {
					merged.put(recipe.key(), recipe.copy());
				}
			}
			this.brewing = new ArrayList<>(merged.values());
		}
	}


	public static class LoadConditionDefinition {
		public String type;
		public String modid;
		public List<String> mods = new ArrayList<>();
		public Boolean any = false;

		public boolean matches() {
			String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
            return switch (normalizedType) {
                case "mod_loaded" -> modid != null && !modid.isBlank() && ModList.get().isLoaded(modid);
                case "all_mods_loaded" ->
                        mods != null && !mods.isEmpty() && mods.stream().allMatch(mod -> mod != null && !mod.isBlank() && ModList.get().isLoaded(mod));
                case "any_mod_loaded" ->
                        mods != null && mods.stream().anyMatch(mod -> mod != null && !mod.isBlank() && ModList.get().isLoaded(mod));
                default -> true;
            };
        }

		public LoadConditionDefinition copy() {
			LoadConditionDefinition copy = new LoadConditionDefinition();
			copy.type = this.type;
			copy.modid = this.modid;
			copy.mods = this.mods == null ? new ArrayList<>() : new ArrayList<>(this.mods);
			copy.any = this.any;
			return copy;
		}
	}

	public static class TierDefinition {
		public String id;
		public Boolean enabled = true;
		public Integer amplifier = 0;
		@SerializedName(value = "multiplier_bonus", alternate = {"multiplierBonus"})
		public Double multiplierBonus;
		@SerializedName(value = "display_name", alternate = {"displayName", "display_suffix", "displaySuffix"})
		public String displayName;

		public int getAmplifier() {
			return amplifier == null ? 0 : amplifier;
		}

		public TierDefinition copy() {
			TierDefinition copy = new TierDefinition();
			copy.id = this.id;
			copy.enabled = this.enabled;
			copy.amplifier = this.amplifier;
			copy.multiplierBonus = this.multiplierBonus;
			copy.displayName = this.displayName;
			return copy;
		}

		public void mergeFrom(TierDefinition incoming) {
			if (incoming.enabled != null) this.enabled = incoming.enabled;
			if (incoming.amplifier != null) this.amplifier = incoming.amplifier;
			if (incoming.multiplierBonus != null) this.multiplierBonus = incoming.multiplierBonus;
			if (incoming.displayName != null) this.displayName = incoming.displayName;
		}
	}

	public static class DurationDefinition {
		public String id;
		public Boolean enabled = true;
		public Integer ticks = 3600;
		@SerializedName(value = "display_name", alternate = {"displayName", "display_suffix", "displaySuffix"})
		public String displayName;

		public int getTicks() {
			return ticks == null ? 3600 : Math.max(1, ticks);
		}

		public DurationDefinition copy() {
			DurationDefinition copy = new DurationDefinition();
			copy.id = this.id;
			copy.enabled = this.enabled;
			copy.ticks = this.ticks;
			copy.displayName = this.displayName;
			return copy;
		}

		public void mergeFrom(DurationDefinition incoming) {
			if (incoming.enabled != null) this.enabled = incoming.enabled;
			if (incoming.ticks != null) this.ticks = incoming.ticks;
			if (incoming.displayName != null) this.displayName = incoming.displayName;
		}
	}

	public static class VariantDefinition {
		public String tier;
		public String duration;
		public Boolean enabled = true;

		public VariantDefinition copy() {
			VariantDefinition copy = new VariantDefinition();
			copy.tier = this.tier;
			copy.duration = this.duration;
			copy.enabled = this.enabled;
			return copy;
		}

		public void mergeFrom(VariantDefinition incoming) {
			if (incoming.enabled != null) this.enabled = incoming.enabled;
		}

		public String key() {
			return normalizeId(this.tier) + ":" + normalizeId(this.duration);
		}
	}

	public static class BrewingRecipeDefinition {
		public Boolean enabled = true;
		@SerializedName(value = "input_potion", alternate = {"inputPotion"})
		public String inputPotion;
		@SerializedName(value = "input_tier", alternate = {"inputTier"})
		public String inputTier;
		@SerializedName(value = "input_duration", alternate = {"inputDuration"})
		public String inputDuration;
		public String ingredient;
		@SerializedName(value = "output_potion", alternate = {"outputPotion"})
		public String outputPotion;
		@SerializedName(value = "output_tier", alternate = {"outputTier"})
		public String outputTier;
		@SerializedName(value = "output_duration", alternate = {"outputDuration"})
		public String outputDuration;

		public BrewingRecipeDefinition copy() {
			BrewingRecipeDefinition copy = new BrewingRecipeDefinition();
			copy.enabled = this.enabled;
			copy.inputPotion = this.inputPotion;
			copy.inputTier = this.inputTier;
			copy.inputDuration = this.inputDuration;
			copy.ingredient = this.ingredient;
			copy.outputPotion = this.outputPotion;
			copy.outputTier = this.outputTier;
			copy.outputDuration = this.outputDuration;
			return copy;
		}

		public String key() {
			String inputKey = inputPotion != null ? inputPotion : normalizeId(inputTier) + ":" + normalizeId(inputDuration);
			String outputKey = outputPotion != null ? outputPotion : normalizeId(outputTier) + ":" + normalizeId(outputDuration);
			return inputKey + "->" + ingredient + "->" + outputKey;
		}
	}

	@Getter
	public static class ResolvedPotionFamily {
		private final PotionFamilyDefinition definition;
		private final String effectReference;
		private final ResourceLocation effectId;
		private final Map<String, TierDefinition> tiers;
		private final Map<String, DurationDefinition> durations;
		private final List<ResolvedPotionVariant> variants;

		public ResolvedPotionFamily(PotionFamilyDefinition definition, String effectReference, ResourceLocation effectId, Map<String, TierDefinition> tiers, Map<String, DurationDefinition> durations, List<ResolvedPotionVariant> variants) {
			this.definition = definition;
			this.effectReference = effectReference;
			this.effectId = effectId;
			this.tiers = tiers;
			this.durations = durations;
			this.variants = variants;
		}

		public String getExpectedEffectNamespace() {
			ResourceLocation id = parseEffectReference(effectReference);
			if (id != null) return id.getNamespace();
			return null;
		}

		public MobEffect resolveMobEffect() {
			ResourceLocation id = parseEffectReference(effectReference);
			return id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
		}

		public String getDisplayName() {
			if (definition.displayName != null && !definition.displayName.isBlank()) {
				return definition.displayName;
			}
			String raw = definition.id == null ? "Potion" : definition.id.replace('_', ' ').replace('-', ' ');
			StringBuilder builder = new StringBuilder();
			for (String part : raw.split(" ")) {
				if (part.isBlank()) {
					continue;
				}
				if (!builder.isEmpty()) {
					builder.append(' ');
				}
				builder.append(Character.toUpperCase(part.charAt(0)));
				if (part.length() > 1) {
					builder.append(part.substring(1));
				}
			}
			return !builder.isEmpty() ? builder.toString() : "Potion";
		}

	}

	@Getter
	public static class ResolvedPotionVariant {
		private final String familyId;
		private final String registryName;
		private final TierDefinition tier;
		private final DurationDefinition duration;
		private final VariantDefinition variant;

		public ResolvedPotionVariant(String familyId, String registryName, TierDefinition tier, DurationDefinition duration, VariantDefinition variant) {
			this.familyId = familyId;
			this.registryName = registryName;
			this.tier = tier;
			this.duration = duration;
			this.variant = variant;
		}

		public String variantKey() {
			return familyId + ":" + tier.id + ":" + duration.id;
		}
	}
}
