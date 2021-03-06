package com.eve.marketwatch.model.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(
        tableName = "eve_marketwatch_item_snapshot"
)
public class ItemSnapshot {

    private String id;
    private long locationId;
    private int typeId;
    private long amount;
    private boolean isBuy;

    @DynamoDBHashKey(
            attributeName = "id"
    )
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public void setBuy(boolean buy) {
        isBuy = buy;
    }

    @Override
    public String toString() {
        return "ItemSnapshot{" +
                "id='" + id + '\'' +
                ", locationId=" + locationId +
                ", typeId=" + typeId +
                ", amount=" + amount +
                ", isBuy=" + isBuy +
                '}';
    }
}
