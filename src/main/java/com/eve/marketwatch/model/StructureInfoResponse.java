package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class StructureInfoResponse {
    private String name;
    @SerializedName("type_id")
    private int typeId;

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
