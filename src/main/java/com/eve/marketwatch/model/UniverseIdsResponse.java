package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class UniverseIdsResponse {
    private Long[] structure;
    private SearchEntry[] characters;
    @SerializedName("inventory_types")
    private SearchEntry[] inventoryTypes;

    public Long[] getStructure() {
        return structure;
    }

    public void setStructure(Long[] structure) {
        this.structure = structure;
    }

    public SearchEntry[] getCharacters() {
        return characters;
    }

    public void setCharacters(SearchEntry[] characters) {
        this.characters = characters;
    }

    public SearchEntry[] getInventoryTypes() {
        return inventoryTypes;
    }

    public void setInventoryTypes(SearchEntry[] inventoryTypes) {
        this.inventoryTypes = inventoryTypes;
    }
}
