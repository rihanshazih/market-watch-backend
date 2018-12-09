package com.eve.marketwatch.model.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;


@DynamoDBTable(
        tableName = "eve_marketwatch_structure"
)
public class Structure {
    private long structureId;
    private String structureName;
    private int typeId;
    // the market service is only visible on stations
    private boolean marketService;

    @DynamoDBHashKey(
            attributeName = "structure_id"
    )
    public long getStructureId() {
        return structureId;
    }

    public void setStructureId(long structureId) {
        this.structureId = structureId;
    }

    public String getStructureName() {
        return structureName;
    }

    public void setStructureName(String structureName) {
        this.structureName = structureName;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public boolean isMarketService() {
        return marketService;
    }

    public void setMarketService(boolean marketService) {
        this.marketService = marketService;
    }
}
