package com.eve.marketwatch.model.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StructureRepository {
    private static final StructureRepository adapter = new StructureRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private StructureRepository() {
    }

    public static StructureRepository getInstance() {
        return adapter;
    }

    public Structure save(final Structure structure) {
        System.out.println("Saving " + structure);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(structure);
        return structure;
    }

    public List<Structure> findAll() {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression();
        return mapper.scan(Structure.class, scanRequest);
    }

    public Optional<Structure> find(long structureId) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withN(String.valueOf(structureId)));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("structure_id = :val1").withExpressionAttributeValues(vals);
        PaginatedScanList<Structure> scan = mapper.scan(Structure.class, scanRequest);
        return Optional.ofNullable(scan.isEmpty() ? null : (Structure)scan.get(0));
    }

    public Optional<Structure> find(final String structureName) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withS(structureName));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("structureName = :val1").withExpressionAttributeValues(vals);
        PaginatedScanList<Structure> scan = mapper.scan(Structure.class, scanRequest);
        return Optional.ofNullable(scan.isEmpty() ? null : (Structure)scan.get(0));
    }
}
