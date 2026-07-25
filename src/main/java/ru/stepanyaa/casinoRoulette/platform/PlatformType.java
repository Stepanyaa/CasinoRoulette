package ru.stepanyaa.casinoRoulette.platform;

public enum PlatformType {

    FOLIA("Folia", Family.BUKKIT, true),

    PURPUR("Purpur", Family.BUKKIT, false),

    PAPER("Paper", Family.BUKKIT, false),

    SPIGOT("Spigot", Family.BUKKIT, false),

    BUKKIT("Bukkit", Family.BUKKIT, false),

    SPONGE("Sponge", Family.SPONGE, false),

    UNKNOWN("Unknown", Family.BUKKIT, false);

    public enum Family { BUKKIT, SPONGE }

    private final String displayName;
    private final Family family;
    private final boolean regionised;

    PlatformType(String displayName, Family family, boolean regionised) {
        this.displayName = displayName;
        this.family = family;
        this.regionised = regionised;
    }

    public String displayName() {
        return displayName;
    }

    public Family family() {
        return family;
    }

    public boolean isRegionised() {
        return regionised;
    }

    public boolean isBukkitFamily() {
        return family == Family.BUKKIT;
    }

    public boolean isSpongeFamily() {
        return family == Family.SPONGE;
    }
}
