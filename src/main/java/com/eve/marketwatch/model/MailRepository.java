package com.eve.marketwatch.model;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailRepository {
    private static final MailRepository adapter = new MailRepository();
    private final AmazonDynamoDB client = ClientProvider.get();

    private MailRepository() {
    }

    public static MailRepository getInstance() {
        return adapter;
    }

    public Mail save(Mail mail) {
        System.out.println("Saving " + mail);
        DynamoDBMapper mapper = new DynamoDBMapper(this.client);
        mapper.save(mail);
        return mail;
    }

    public List<Mail> findByStatus(final MailStatus status) {
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":val1", new AttributeValue().withS(status.name()));
        DynamoDBScanExpression scanRequest = new DynamoDBScanExpression()
                .withFilterExpression("mailStatus = :val1")
                .withExpressionAttributeValues(vals);
        return mapper.scan(Mail.class, scanRequest);
    }

}
