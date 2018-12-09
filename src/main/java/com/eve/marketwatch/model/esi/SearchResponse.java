package com.eve.marketwatch.model.esi;

import com.google.gson.annotations.SerializedName;

public class SearchResponse {
    private Long[] station;
    private Long[] structure;
    @SerializedName("inventory_type")
    private Integer[] inventoryTypes;

    public Long[] getStructure() {
        return structure;
    }

    public void setStructure(Long[] structure) {
        this.structure = structure;
    }

    public Integer[] getInventoryTypes() {
        return inventoryTypes;
    }

    public void setInventoryTypes(Integer[] inventoryTypes) {
        this.inventoryTypes = inventoryTypes;
    }

    public Long[] getStation() {
        return station;
    }

    public void setStation(Long[] station) {
        this.station = station;
    }
}
