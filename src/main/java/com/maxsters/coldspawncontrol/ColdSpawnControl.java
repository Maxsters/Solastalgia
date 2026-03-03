package com.maxsters.coldspawncontrol;

import com.maxsters.coldspawncontrol.registry.ModBlocks;
import com.maxsters.coldspawncontrol.registry.ModItems;
import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import com.maxsters.coldspawncontrol.registry.ModFeatures;
import com.maxsters.coldspawncontrol.registry.ModConfiguredFeatures;
import com.maxsters.coldspawncontrol.init.ModParticles;
import com.maxsters.coldspawncontrol.registry.ModPlacedFeatures;
import com.maxsters.coldspawncontrol.registry.ModBiomeModifiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ColdSpawnControl.MOD_ID)
@SuppressWarnings("null")
public class ColdSpawnControl {
    public static final String MOD_ID = "solastalgia";
    public static final Logger LOGGER = LogManager.getLogger();

    public ColdSpawnControl() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.maxsters.coldspawncontrol.registry.ModEntities.register(modBus);
        ModSoundEvents.SOUND_EVENTS.register(modBus);
        ModParticles.register(modBus);
        ModFeatures.register(modBus);
        ModConfiguredFeatures.register(modBus);
        ModPlacedFeatures.register(modBus);
        ModBiomeModifiers.register(modBus);
        com.maxsters.coldspawncontrol.registry.ModLootModifiers.register(modBus);

        modBus.addListener(this::commonSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        // Register GameRules immediately (works because they are static)
        com.maxsters.coldspawncontrol.config.ModGameRules.register();

        LOGGER.info("{} initialised.", MOD_ID);
    }

    private void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.maxsters.coldspawncontrol.command.SolastalgiaCommand.register(event.getDispatcher());
    }

    private void commonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.maxsters.coldspawncontrol.network.Networking.register();
        });
    }
}
