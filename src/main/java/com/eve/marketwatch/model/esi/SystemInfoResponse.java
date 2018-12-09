package com.eve.marketwatch.model.esi;

import com.google.gson.annotations.SerializedName;

public class SystemInfoResponse {
    @SerializedName("constellation_id")
    private int constellationId;

    public int getConstellationId() {
        return constellationId;
    }

    public void setConstellationId(int constellationId) {
        this.constellationId = constellationId;
    }
}
