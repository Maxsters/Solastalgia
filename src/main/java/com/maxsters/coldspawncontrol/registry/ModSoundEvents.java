package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSoundEvents {
        public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister
                        .create(ForgeRegistries.SOUND_EVENTS, ColdSpawnControl.MOD_ID);

        public static final RegistryObject<SoundEvent> AMBIENCE = SOUND_EVENTS.register("ambience",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "ambience"), 0.0F));

        public static final RegistryObject<SoundEvent> FORGET = SOUND_EVENTS.register("forget",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "forget"), 16.0F));

        // Paranoia sounds use variable range (0.0F) for proper positional audio
        public static final RegistryObject<SoundEvent> PARANOIA_ENVIRONMENT = SOUND_EVENTS.register(
                        "paranoia.environment",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.environment"), 0.0F));

        public static final RegistryObject<SoundEvent> PARANOIA_CAVE = SOUND_EVENTS.register(
                        "paranoia.cave",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.cave"), 0.0F));

        public static final RegistryObject<SoundEvent> PARANOIA_OUTSIDE = SOUND_EVENTS.register(
                        "paranoia.outside",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.outside"), 0.0F));

        public static final RegistryObject<SoundEvent> SHADOW_FLICKER_APPEARANCE = SOUND_EVENTS.register(
                        "paranoia.shadow_flicker.appearance",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID,
                                                        "paranoia.shadow_flicker.appearance"),
                                        0.0F));

        // Subtitle Hallucinations
        public static final RegistryObject<SoundEvent> SUBTITLE_CONFUSION = SOUND_EVENTS
                        .register("paranoia.subtitle.confusion", () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.confusion")));
        public static final RegistryObject<SoundEvent> SUBTITLE_GRIEF = SOUND_EVENTS.register("paranoia.subtitle.grief",
                        () -> new SoundEvent(new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.grief")));
        public static final RegistryObject<SoundEvent> SUBTITLE_DISORIENTATION = SOUND_EVENTS.register(
                        "paranoia.subtitle.disorientation",
                        () -> new SoundEvent(new ResourceLocation(ColdSpawnControl.MOD_ID,
                                        "paranoia.subtitle.disorientation")));
        public static final RegistryObject<SoundEvent> SUBTITLE_DESPAIR = SOUND_EVENTS
                        .register("paranoia.subtitle.despair", () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.despair")));
        public static final RegistryObject<SoundEvent> SUBTITLE_MUSCLE_MEMORY = SOUND_EVENTS.register(
                        "paranoia.subtitle.muscle_memory",
                        () -> new SoundEvent(new ResourceLocation(ColdSpawnControl.MOD_ID,
                                        "paranoia.subtitle.muscle_memory")));
        public static final RegistryObject<SoundEvent> SUBTITLE_PARANOIA = SOUND_EVENTS
                        .register("paranoia.subtitle.paranoia", () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.paranoia")));
        public static final RegistryObject<SoundEvent> SUBTITLE_STARVATION = SOUND_EVENTS
                        .register("paranoia.subtitle.starvation", () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.starvation")));
        public static final RegistryObject<SoundEvent> SUBTITLE_GUILT = SOUND_EVENTS.register("paranoia.subtitle.guilt",
                        () -> new SoundEvent(new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.guilt")));
        public static final RegistryObject<SoundEvent> SUBTITLE_FALSE_HOPE = SOUND_EVENTS
                        .register("paranoia.subtitle.false_hope", () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "paranoia.subtitle.false_hope")));
        public static final RegistryObject<SoundEvent> SUBTITLE_DISSOCIATION = SOUND_EVENTS.register(
                        "paranoia.subtitle.dissociation",
                        () -> new SoundEvent(new ResourceLocation(ColdSpawnControl.MOD_ID,
                                        "paranoia.subtitle.dissociation")));

        // Main menu music - uses fixed range for consistent volume
        public static final RegistryObject<SoundEvent> MENU_MUSIC = SOUND_EVENTS.register(
                        "menu_music",
                        () -> new SoundEvent(
                                        new ResourceLocation(ColdSpawnControl.MOD_ID, "menu_music"), 16.0F));

        private ModSoundEvents() {
        }
}
