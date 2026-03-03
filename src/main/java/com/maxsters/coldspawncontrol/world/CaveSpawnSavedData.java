package com.maxsters.coldspawncontrol.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class CaveSpawnSavedData extends SavedData {
    private static final String DATA_NAME = "coldspawn_cavespawn";
    private boolean isSpawnSet = false;

    public boolean isSpawnSet() {
        return isSpawnSet;
    }

    public void setSpawnSet(boolean isSpawnSet) {
        this.isSpawnSet = isSpawnSet;
        this.setDirty();
    }

    public static CaveSpawnSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CaveSpawnSavedData::load, CaveSpawnSavedData::create, DATA_NAME);
    }

    public static CaveSpawnSavedData load(CompoundTag tag) {
        CaveSpawnSavedData data = new CaveSpawnSavedData();
        data.isSpawnSet = tag.getBoolean("isSpawnSet");
        return data;
    }

    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag) {
        tag.putBoolean("isSpawnSet", isSpawnSet);
        return tag;
    }

    public static CaveSpawnSavedData create() {
        return new CaveSpawnSavedData();
    }
}
