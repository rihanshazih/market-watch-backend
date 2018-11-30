package com.eve.marketwatch.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

import java.util.Date;


@DynamoDBTable(
        tableName = "eve_marketwatch_user"
)
public class User {
    private int characterId;
    private String refreshToken;
    private String accessToken;
    private Date accessTokenExpiry;

    @DynamoDBHashKey(
            attributeName = "character_id"
    )
    public int getCharacterId() {
        return characterId;
    }

    public void setCharacterId(int characterId) {
        this.characterId = characterId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Date getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public void setAccessTokenExpiry(Date accessTokenExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
    }

    @Override
    public String toString() {
        return "User{" +
                "characterId=" + characterId +
                ", refreshToken='" + refreshToken + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", accessTokenExpiry=" + accessTokenExpiry +
                '}';
    }
}
