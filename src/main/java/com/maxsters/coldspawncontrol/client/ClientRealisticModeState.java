package com.maxsters.coldspawncontrol.client;

/**
 * Client-side state for Realistic Mode.
 * Synced from server via RealisticModePacket.
 */
public final class ClientRealisticModeState {

    private ClientRealisticModeState() {
    }

    /**
     * Whether Realistic Mode is enabled.
     * Default to true until synced.
     */
    public static boolean isRealisticModeEnabled = true;
    public static boolean renderCloudCoverage = false;
}
