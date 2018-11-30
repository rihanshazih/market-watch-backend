package com.eve.marketwatch.model.esi;

import com.google.gson.annotations.SerializedName;

public class MailRecipient {

    @SerializedName("recipient_type")
    private final String recipientType;
    @SerializedName("recipient_id")
    private final int recipientId;

    public MailRecipient(int recipientId) {
        this.recipientId = recipientId;
        this.recipientType = "character";
    }

    public MailRecipient(final String recipientType, final int recipientId) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public int getRecipientId() {
        return recipientId;
    }

    @Override
    public String toString() {
        return "MailRecipient{" +
                "recipientType='" + recipientType + '\'' +
                ", recipientId=" + recipientId +
                '}';
    }
}
