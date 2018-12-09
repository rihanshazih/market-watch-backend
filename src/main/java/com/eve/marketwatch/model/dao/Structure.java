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
    private boolean npcStation;
    // this field should be nullable, as we allow for lazy resolution within the MarketParser to not slow down
    // the structure search on user-facing endpoints
    private Integer regionId;

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

    public boolean isNpcStation() {
        return npcStation;
    }

    public void setNpcStation(boolean npcStation) {
        this.npcStation = npcStation;
    }

    public Integer getRegionId() {
        return regionId;
    }

    public void setRegionId(Integer regionId) {
        this.regionId = regionId;
    }
}
