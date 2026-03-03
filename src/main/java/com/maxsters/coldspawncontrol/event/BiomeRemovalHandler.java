package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public final class BiomeRemovalHandler {
    // Mirrors the biome_replacer.properties deny list so these never appear in new
    // chunks.
    private static final Set<ResourceLocation> DISABLED_BIOMES = Set.of(
            new ResourceLocation("minecraft:desert"),
            new ResourceLocation("minecraft:badlands"),
            new ResourceLocation("minecraft:eroded_badlands"),
            new ResourceLocation("minecraft:wooded_badlands"),
            new ResourceLocation("minecraft:savanna"),
            new ResourceLocation("minecraft:windswept_savanna"),
            new ResourceLocation("minecraft:jungle"),
            new ResourceLocation("minecraft:sparse_jungle"),
            new ResourceLocation("minecraft:bamboo_jungle"),
            new ResourceLocation("minecraft:mangrove_swamp"),
            new ResourceLocation("minecraft:warm_ocean"),
            new ResourceLocation("minecraft:lukewarm_ocean"),
            new ResourceLocation("minecraft:deep_lukewarm_ocean"),
            new ResourceLocation("minecraft:dark_forest"),
            new ResourceLocation("minecraft:mushroom_fields"),
            new ResourceLocation("minecraft:forest"),
            new ResourceLocation("minecraft:flower_forest"),
            new ResourceLocation("minecraft:pale_garden"),
            new ResourceLocation("minecraft:sunflower_plains"),
            new ResourceLocation("minecraft:snowy_plains"),
            new ResourceLocation("minecraft:frozen_river"),
            new ResourceLocation("minecraft:snowy_beach"),
            new ResourceLocation("minecraft:snowy_taiga"),
            new ResourceLocation("minecraft:savanna_plateau"),
            new ResourceLocation("minecraft:old_growth_birch_forest"),
            new ResourceLocation("minecraft:birch_forest"),
            new ResourceLocation("minecraft:meadow"),
            new ResourceLocation("minecraft:swamp"),
            new ResourceLocation("minecraft:ocean"),
            new ResourceLocation("minecraft:deep_ocean"),
            new ResourceLocation("minecraft:deep_frozen_ocean"));

    private static final Field PARAMETER_LIST_FIELD;
    private static final ResourceKey<Registry<Biome>> BIOME_REGISTRY_KEY = Registry.BIOME_REGISTRY;

    static {
        Field field = null;
        try {
            for (Field f : MultiNoiseBiomeSource.class.getDeclaredFields()) {
                if (f.getType() == Climate.ParameterList.class) {
                    field = f;

                    break;
                }
            }
        } catch (Exception e) {
            ColdSpawnControl.LOGGER.error("Failed to find MultiNoiseBiomeSource parameters field by type", e);
        }

        if (field != null) {
            field.setAccessible(true);
        } else {
            ColdSpawnControl.LOGGER
                    .error("PARAMETER_LIST_FIELD is null! Could not find field of type Climate.ParameterList");
        }
        PARAMETER_LIST_FIELD = field;
    }

    private BiomeRemovalHandler() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ResourceKey<Level> levelKey = level.dimension();
        if (!Level.OVERWORLD.equals(levelKey)) {
            return;
        }

        // ColdSpawnControl.LOGGER.info("Attempting to prune biomes for level {}",
        // levelKey);
        pruneOverworldBiomes(level);
    }

    private static void pruneOverworldBiomes(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = generator.getBiomeSource();
        if (!(biomeSource instanceof MultiNoiseBiomeSource multiNoise)) {
            ColdSpawnControl.LOGGER.warn(
                    "Skipping biome pruning for {}: biome source is {}",
                    level.dimension().location(),
                    biomeSource.getClass().getSimpleName());
            return;
        }

        ResourceKey<Registry<Biome>> biomeRegistryKey = Objects.requireNonNull(BIOME_REGISTRY_KEY,
                "biome registry key");
        Set<ResourceKey<Biome>> blockedKeys = DISABLED_BIOMES.stream()
                .map(id -> ResourceKey.create(biomeRegistryKey, Objects.requireNonNull(id, "biome id")))
                .collect(Collectors.toSet());

        Optional<Climate.ParameterList<Holder<Biome>>> parametersOpt = readParameterList(multiNoise);
        if (parametersOpt.isEmpty()) {
            ColdSpawnControl.LOGGER.error("Failed to access MultiNoise biome parameters; cannot prune biomes.");
            return;
        }

        Climate.ParameterList<Holder<Biome>> parameters = parametersOpt
                .orElseThrow(() -> new IllegalStateException("MultiNoise biome parameters missing"));
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> entries = Objects
                .requireNonNull(parameterEntries(parameters));
        Registry<Biome> biomeRegistry = Objects.requireNonNull(
                level.registryAccess().registryOrThrow(biomeRegistryKey),
                "biome registry");

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> converted = new ArrayList<>();
        Set<ResourceLocation> targets = new HashSet<>();

        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : entries) {
            Holder<Biome> cold = selectColdBiome(
                    Objects.requireNonNull(pair.getSecond(), "biome holder"),
                    biomeRegistry,
                    blockedKeys);
            targets.add(cold.unwrapKey().map(ResourceKey::location)
                    .orElseGet(() -> new ResourceLocation("unknown", "unknown")));
            converted.add(Pair.of(pair.getFirst(), cold));
        }

        if (!writeParameterList(multiNoise, new Climate.ParameterList<>(converted))) {
            ColdSpawnControl.LOGGER.error("Failed to update MultiNoise biome parameters after filtering.");
            return;
        }

        biomeSource.possibleBiomes().clear();
        biomeSource.possibleBiomes().addAll(converted.stream().map(Pair::getSecond).collect(Collectors.toSet()));

    }

    @SuppressWarnings("unchecked")
    private static Optional<Climate.ParameterList<Holder<Biome>>> readParameterList(MultiNoiseBiomeSource source) {
        if (PARAMETER_LIST_FIELD == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable((Climate.ParameterList<Holder<Biome>>) PARAMETER_LIST_FIELD.get(source));
        } catch (Exception ex) {
            ColdSpawnControl.LOGGER.error("Error reading MultiNoise biome parameters", ex);
            return Optional.empty();
        }
    }

    private static boolean writeParameterList(MultiNoiseBiomeSource source,
            Climate.ParameterList<Holder<Biome>> replacement) {
        if (PARAMETER_LIST_FIELD == null) {
            return false;
        }
        try {
            PARAMETER_LIST_FIELD.set(source, replacement);
            return true;
        } catch (Exception ex) {
            ColdSpawnControl.LOGGER.error("Error replacing MultiNoise biome parameters", ex);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameterEntries(
            Climate.ParameterList<Holder<Biome>> list) {
        try {
            Method values = Climate.ParameterList.class.getDeclaredMethod("values");
            values.setAccessible(true);
            return (List<Pair<Climate.ParameterPoint, Holder<Biome>>>) values.invoke(list);
        } catch (Exception ignored) {
            // fall through to field access
        }
        // Parchment: "values" (obfuscated: f_186846_) - fallback field access
        for (String fieldName : new String[] { "values", "f_186846_" }) {
            try {
                Field field = Climate.ParameterList.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(list);
                if (value instanceof List) {
                    return (List<Pair<Climate.ParameterPoint, Holder<Biome>>>) value;
                }
            } catch (Exception ignored) {
                // try next name
            }
        }

        ColdSpawnControl.LOGGER
                .error("Could not access Climate.ParameterList values; returning empty list to avoid crash.");
        return List.of();
    }

    private static Holder<Biome> selectColdBiome(Holder<Biome> original, Registry<Biome> registry,
            Set<ResourceKey<Biome>> blockedKeys) {
        Objects.requireNonNull(original, "original biome holder");
        Objects.requireNonNull(registry, "biome registry");
        Objects.requireNonNull(blockedKeys, "blocked keys");

        ResourceKey<Biome> defaultKey = Objects.requireNonNull(Biomes.SNOWY_PLAINS, "default biome key");
        ResourceKey<Biome> fallback = Objects.requireNonNull(Biomes.SNOWY_TAIGA, "fallback biome key");
        Holder<Biome> defaultBiome = registry.getHolder(defaultKey)
                .orElseGet(() -> registry.getHolder(fallback)
                        .orElseThrow(() -> new IllegalStateException("Missing fallback biome holder")));

        Optional<ResourceKey<Biome>> keyOpt = original.unwrapKey();
        if (keyOpt.isPresent() && blockedKeys.contains(keyOpt.get())) {
            return defaultBiome;
        }

        ResourceLocation id = keyOpt.map(ResourceKey::location).orElse(new ResourceLocation("minecraft", "unknown"));
        String path = id.getPath().toLowerCase(Locale.ROOT);

        ResourceKey<Biome> frozenRiver = Objects.requireNonNull(Biomes.FROZEN_RIVER, "frozen river biome key");
        ResourceKey<Biome> frozenPeaks = Objects.requireNonNull(Biomes.FROZEN_PEAKS, "frozen peaks biome key");
        ResourceKey<Biome> snowyTaiga = Objects.requireNonNull(Biomes.SNOWY_TAIGA, "snowy taiga biome key");
        ResourceKey<Biome> iceSpikes = Objects.requireNonNull(Biomes.ICE_SPIKES, "ice spikes biome key");
        ResourceKey<Biome> snowyBeach = Objects.requireNonNull(Biomes.SNOWY_BEACH, "snowy beach biome key");

        if (path.contains("ocean")) {
            return defaultBiome;
        }
        if (path.contains("river")) {
            return registry.getHolder(frozenRiver).orElse(defaultBiome);
        }
        if (path.contains("peak") || path.contains("mountain") || path.contains("hill") || path.contains("ridge")) {
            return registry.getHolder(frozenPeaks).orElse(defaultBiome);
        }
        if (path.contains("taiga") || path.contains("forest") || path.contains("pine")) {
            return registry.getHolder(snowyTaiga).orElse(defaultBiome);
        }
        if (path.contains("ice_spike")) {
            return registry.getHolder(iceSpikes).orElse(defaultBiome);
        }
        if (path.contains("beach")) {
            return registry.getHolder(snowyBeach).orElse(defaultBiome);
        }

        int mix = Mth.abs(id.toString().hashCode());
        return switch (mix % 4) {
            case 0 -> registry.getHolder(defaultKey).orElse(defaultBiome);
            case 1 -> registry.getHolder(iceSpikes).orElse(defaultBiome);
            case 2 -> registry.getHolder(snowyTaiga).orElse(defaultBiome);
            default -> registry.getHolder(frozenPeaks).orElse(defaultBiome);
        };
    }
}
