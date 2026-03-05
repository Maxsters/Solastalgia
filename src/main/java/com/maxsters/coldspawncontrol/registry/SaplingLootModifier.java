package com.maxsters.coldspawncontrol.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Global loot modifier that gives every Overworld chest loot table
 * a rare chance to contain an oak or spruce sapling.
 * Saplings are precious in a frozen world.
 */
public class SaplingLootModifier extends LootModifier {

    @SuppressWarnings("null")
    public static final Codec<SaplingLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).and(
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(m -> m.chance))
            .apply(inst, SaplingLootModifier::new));

    private final float chance;

    public SaplingLootModifier(LootItemCondition[] conditionsIn, float chance) {
        super(conditionsIn);
        this.chance = chance;
    }

    @SuppressWarnings("null")
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLootIn,
            LootContext contextIn) {
        net.minecraft.resources.ResourceLocation queriedLootTableId = contextIn.getQueriedLootTableId();
        if (queriedLootTableId == null || !queriedLootTableId.getPath().contains("chests/")) {
            return generatedLootIn;
        }

        if (contextIn.getLevel() == null
                || contextIn.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return generatedLootIn;
        }

        if (contextIn.getRandom().nextFloat() < chance) {
            if (contextIn.getRandom().nextBoolean()) {
                generatedLootIn.add(new ItemStack(Items.OAK_SAPLING, 1));
            } else {
                generatedLootIn.add(new ItemStack(Items.SPRUCE_SAPLING, 1));
            }
        }

        return generatedLootIn;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
