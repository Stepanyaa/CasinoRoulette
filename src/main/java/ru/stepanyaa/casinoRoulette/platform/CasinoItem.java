package ru.stepanyaa.casinoRoulette.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CasinoItem {

    private final String materialId;
    private final int amount;
    private final String displayName;
    private final List<String> lore;
    private final boolean glowing;
    private final boolean hideAttributes;
    private final Integer customModelData;
    private final java.util.UUID skullOwner;

    private CasinoItem(Builder builder) {
        this.materialId = builder.materialId;
        this.amount = builder.amount;
        this.displayName = builder.displayName;
        this.lore = Collections.unmodifiableList(new ArrayList<>(builder.lore));
        this.glowing = builder.glowing;
        this.hideAttributes = builder.hideAttributes;
        this.customModelData = builder.customModelData;
        this.skullOwner = builder.skullOwner;
    }

    public static Builder of(String materialId) {
        return new Builder(materialId);
    }

    public static CasinoItem simple(String materialId, String displayName) {
        return of(materialId).name(displayName).build();
    }

    public String materialId() {
        return materialId;
    }

    public int amount() {
        return amount;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public boolean glowing() {
        return glowing;
    }

    public boolean hideAttributes() {
        return hideAttributes;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public java.util.UUID skullOwner() {
        return skullOwner;
    }

    public static final class Builder {
        private final String materialId;
        private int amount = 1;
        private String displayName;
        private final List<String> lore = new ArrayList<>();
        private boolean glowing;
        private boolean hideAttributes = true;
        private Integer customModelData;
        private java.util.UUID skullOwner;

        private Builder(String materialId) {
            this.materialId = materialId;
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, amount);
            return this;
        }

        public Builder name(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder lore(String... lines) {
            Collections.addAll(this.lore, lines);
            return this;
        }

        public Builder lore(List<String> lines) {
            if (lines != null) {
                this.lore.addAll(lines);
            }
            return this;
        }

        public Builder glowing(boolean glowing) {
            this.glowing = glowing;
            return this;
        }

        public Builder hideAttributes(boolean hideAttributes) {
            this.hideAttributes = hideAttributes;
            return this;
        }

        public Builder modelData(Integer customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        public Builder skullOwner(java.util.UUID skullOwner) {
            this.skullOwner = skullOwner;
            return this;
        }

        public CasinoItem build() {
            return new CasinoItem(this);
        }
    }
}
