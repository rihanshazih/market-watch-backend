package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class UniverseIdsResponse {
    @SerializedName("inventory_types")
    private SearchEntry[] inventoryTypes;

    public SearchEntry[] getInventoryTypes() {
        return inventoryTypes;
    }

    public void setInventoryTypes(SearchEntry[] inventoryTypes) {
        this.inventoryTypes = inventoryTypes;
    }
}
