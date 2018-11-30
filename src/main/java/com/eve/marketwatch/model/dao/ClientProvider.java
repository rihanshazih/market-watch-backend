package com.eve.marketwatch.model.dao;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

public class ClientProvider {
    private ClientProvider() {
    }

    static AmazonDynamoDB get() {
        final String awsRegion = System.getenv("REGION");
        return (AmazonDynamoDB)((AmazonDynamoDBClientBuilder)AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder
                        .EndpointConfiguration("https://dynamodb." + awsRegion + ".amazonaws.com", awsRegion))).build();
    }
}
