package com.eve.marketwatch.model.esi;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StationInfoResponse {
    private String name;
    @SerializedName("type_id")
    private int typeId;
    private List<String> services;
    @SerializedName("system_id")
    private int systemId;

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

    public List<String> getServices() {
        return services;
    }

    public void setServices(List<String> services) {
        this.services = services;
    }

    public int getSystemId() {
        return systemId;
    }

    public void setSystemId(int systemId) {
        this.systemId = systemId;
    }
}
