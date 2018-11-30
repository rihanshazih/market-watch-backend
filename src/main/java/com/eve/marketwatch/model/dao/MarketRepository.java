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

public class MarketRepository {
    private static final MarketRepository adapter = new MarketRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private MarketRepository() {
    }

    public static MarketRepository getInstance() {
        return adapter;
    }

    public Market save(Market market) {
        System.out.println("Saving " + market);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(market);
        return market;
    }

    public List<Market> findAll() {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression();
        return mapper.scan(Market.class, scanRequest);
    }

    public Optional<Market> find(long locationId) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withN(String.valueOf(locationId)));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("location_id = :val1").withExpressionAttributeValues(vals);
        PaginatedScanList<Market> scan = mapper.scan(Market.class, scanRequest);
        return Optional.ofNullable(scan.isEmpty() ? null : (Market)scan.get(0));
    }

    public Optional<Market> find(String locationName) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withS(locationName));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("locationName = :val1").withExpressionAttributeValues(vals);
        PaginatedScanList<Market> scan = mapper.scan(Market.class, scanRequest);
        return Optional.ofNullable(scan.isEmpty() ? null : (Market)scan.get(0));
    }
}
