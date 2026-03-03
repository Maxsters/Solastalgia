package com.maxsters.coldspawncontrol.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public class OminousBookModifier extends LootModifier {

    @SuppressWarnings("null")
    public static final Codec<OminousBookModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).and(
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(m -> m.chance))
            .apply(inst, OminousBookModifier::new));

    private final float chance;

    public OminousBookModifier(LootItemCondition[] conditionsIn, float chance) {
        super(conditionsIn);
        this.chance = chance;
    }

    @SuppressWarnings("null")
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLootIn,
            LootContext contextIn) {
        net.minecraft.resources.ResourceLocation queriedLootTableId = contextIn.getQueriedLootTableId();
        if (queriedLootTableId != null && queriedLootTableId.getPath().contains("chests/")) {
            if (contextIn.getLevel() != null
                    && contextIn.getLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                // Check if debug mode is active
                boolean debugMode = com.maxsters.coldspawncontrol.config.ModGameRules.RULE_DEBUG_LOOT_BOOK != null
                        && contextIn.getLevel().getGameRules()
                                .getBoolean(com.maxsters.coldspawncontrol.config.ModGameRules.RULE_DEBUG_LOOT_BOOK);

                // Skip generation if book already acquired (unless debug mode)
                if (!debugMode) {
                    OminousBookTracker tracker = OminousBookTracker.get(contextIn.getLevel());
                    if (tracker.isBookAcquired()) {
                        return generatedLootIn;
                    }
                }

                float effectiveChance = debugMode ? 1.0F : chance;
                if (contextIn.getRandom().nextFloat() < effectiveChance) {
                    generatedLootIn.add(OminousBookFactory.createBook());
                }
            }
        }
        return generatedLootIn;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
