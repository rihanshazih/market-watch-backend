package com.eve.marketwatch.model.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemWatchRepository {
    private static final ItemWatchRepository adapter = new ItemWatchRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private ItemWatchRepository() {
    }

    public static ItemWatchRepository getInstance() {
        return adapter;
    }

    public ItemWatch save(ItemWatch itemWatch) {
        System.out.println("Saving " + itemWatch);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(itemWatch);
        return itemWatch;
    }

    public List<ItemWatch> findAll() {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression();
        return mapper.scan(ItemWatch.class, scanRequest);
    }

    public List<ItemWatch> findByCharacterId(int characterId) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withN(String.valueOf(characterId)));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("characterId = :val1").withExpressionAttributeValues(vals);
        return mapper.scan(ItemWatch.class, scanRequest);
    }

    public void delete(final ItemWatch itemWatch) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.delete(itemWatch);
    }
}
