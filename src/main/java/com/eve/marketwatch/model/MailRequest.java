package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MailRequest {

    private List<MailRecipient> recipients;
    private String subject;
    private String body;
    @SerializedName("approved_cost")

    public List<MailRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(final List<MailRecipient> recipients) {
        this.recipients = recipients;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(final String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "MailRequest{" +
                "recipients=" + recipients +
                ", subject='" + subject + '\'' +
                '}';
    }
}
