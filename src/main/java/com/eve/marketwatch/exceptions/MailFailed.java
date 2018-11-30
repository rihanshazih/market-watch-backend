package com.eve.marketwatch.exceptions;

public class MailFailed extends Throwable {
    private final int status;
    private final int recipientId;

    public MailFailed(int status, int recipientId) {
        this.status = status;
        this.recipientId = recipientId;
    }

    public int getStatus() {
        return status;
    }

    public int getRecipientId() {
        return recipientId;
    }
}
