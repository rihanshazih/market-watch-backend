package com.eve.marketwatch.model;

public class BulkMailRequest {
    private int testTargetId;
    private String subject;
    private String text;

    public int getTestTargetId() {
        return testTargetId;
    }

    public void setTestTargetId(int testTargetId) {
        this.testTargetId = testTargetId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
