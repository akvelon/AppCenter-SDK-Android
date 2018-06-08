package com.microsoft.appcenter.assets.enums;

import com.google.gson.annotations.SerializedName;

/**
 * Indicates the state that an update is currently in.
 */
public enum AssetsUpdateState {

    /**
     * Indicates that an update represents the
     * version of the app that is currently running.
     */
    @SerializedName("0")
    RUNNING(0),

    /**
     * Indicates than an update has been installed, but the
     * app hasn't been restarted yet in order to apply it.
     */
    @SerializedName("1")
    PENDING(1),

    /**
     * Indicates than an update represents the latest available
     * release, and can be either currently running or pending.
     */
    @SerializedName("2")
    LATEST(2);

    private final int value;

    AssetsUpdateState(int value) {
        this.value = value;
    }

    /**
     * Gets the assigned enum value.
     *
     * @return integer assigned to enum item.
     */
    public int getValue() {
        return this.value;
    }
}