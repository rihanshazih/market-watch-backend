package com.eve.marketwatch.model.esi;

import com.google.gson.annotations.SerializedName;

public class ConstellationInfoResponse {
    @SerializedName("region_id")
    private int regionId;

    public int getRegionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }
}
