package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class MarketOrderResponse {

    @SerializedName("is_buy_order")
    private boolean isBuyOrder;

    @SerializedName("location_id")
    private long locationId;

    @SerializedName("type_id")
    private int typeId;

    @SerializedName("volume_remain")
    private int volumeRemain;

    public boolean isBuyOrder() {
        return isBuyOrder;
    }

    public void setBuyOrder(boolean buyOrder) {
        isBuyOrder = buyOrder;
    }

    public long getLocationId() {
        return locationId;
    }

    public void setLocationId(long locationId) {
        this.locationId = locationId;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public int getVolumeRemain() {
        return volumeRemain;
    }

    public void setVolumeRemain(int volumeRemain) {
        this.volumeRemain = volumeRemain;
    }

    @Override
    public String toString() {
        return "MarketOrderResponse{" +
                "isBuyOrder=" + isBuyOrder +
                ", locationId=" + locationId +
                ", typeId=" + typeId +
                ", volumeRemain=" + volumeRemain +
                '}';
    }
}
