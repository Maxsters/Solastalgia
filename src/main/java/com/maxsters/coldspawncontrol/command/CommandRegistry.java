package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        VisibilityCommand.register(event.getDispatcher());
        WeatherDebugCommand.register(event.getDispatcher());
        LootBookDebugCommand.register(event.getDispatcher());
    }
}
