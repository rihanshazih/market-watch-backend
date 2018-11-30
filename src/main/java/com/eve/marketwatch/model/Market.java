package com.eve.marketwatch.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable(
        tableName = "eve_marketwatch_market"
)
public class Market {
    private long locationId;
    private String locationName;
    private int typeId;
    // todo: update if this expires
    private int accessorCharacterId;

    @DynamoDBHashKey(
            attributeName = "location_id"
    )
    public long getLocationId() {
        return locationId;
    }

    public void setLocationId(long locationId) {
        this.locationId = locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public int getAccessorCharacterId() {
        return accessorCharacterId;
    }

    public void setAccessorCharacterId(int accessorCharacterId) {
        this.accessorCharacterId = accessorCharacterId;
    }
}
