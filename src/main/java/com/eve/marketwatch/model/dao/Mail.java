package com.eve.marketwatch.model.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAutoGeneratedKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import java.util.Date;

@DynamoDBTable(
        tableName = "eve_marketwatch_mail"
)
public class Mail {

    private String id;
    private int recipient;
    private String subject;
    private String text;
    private MailStatus mailStatus;
    private Date created;

    @DynamoDBAutoGeneratedKey
    @DynamoDBHashKey(
            attributeName = "id"
    )
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getRecipient() {
        return recipient;
    }

    public void setRecipient(int recipient) {
        this.recipient = recipient;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @DynamoDBTypeConvertedEnum
    public MailStatus getMailStatus() {
        return mailStatus;
    }

    public void setMailStatus(MailStatus mailStatus) {
        this.mailStatus = mailStatus;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}