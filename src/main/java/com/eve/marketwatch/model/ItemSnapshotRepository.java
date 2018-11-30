package com.eve.marketwatch.model;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;

import java.util.List;

public class ItemSnapshotRepository {
    private static final ItemSnapshotRepository adapter = new ItemSnapshotRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private ItemSnapshotRepository() {
    }

    public static ItemSnapshotRepository getInstance() {
        return adapter;
    }

    public ItemSnapshot save(ItemSnapshot itemSnapshot) {
        System.out.println("Saving " + itemSnapshot);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(itemSnapshot);
        return itemSnapshot;
    }

    public List<ItemSnapshot> findAll() {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression();
        return mapper.scan(ItemSnapshot.class, scanRequest);
    }
}
