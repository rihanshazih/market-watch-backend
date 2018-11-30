package com.eve.marketwatch.model;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserRepository {
    private static final UserRepository adapter = new UserRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private UserRepository() {
    }

    public static UserRepository getInstance() {
        return adapter;
    }

    public User save(User user) {
        System.out.println("Saving " + user);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(user);
        return user;
    }

    public List<User> findAll() {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression();
        return mapper.scan(User.class, scanRequest);
    }

    public Optional<User> find(int characterId) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        Map<String, AttributeValue> vals = new HashMap();
        vals.put(":val1", (new AttributeValue()).withN(String.valueOf(characterId)));
        DynamoDBScanExpression scanRequest = (new DynamoDBScanExpression()).withFilterExpression("character_id = :val1").withExpressionAttributeValues(vals);
        PaginatedScanList<User> scan = mapper.scan(User.class, scanRequest);
        return Optional.ofNullable(scan.isEmpty() ? null : (User)scan.get(0));
    }

    public void delete(User user) {
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.delete(user);
    }
}
