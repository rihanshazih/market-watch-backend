package com.eve.marketwatch.exceptions;

public class MailFailed extends Throwable {
    private final int status;
    private final int recipientId;
    private final boolean retry;

    public MailFailed(int status, int recipientId, boolean retry) {
        this.status = status;
        this.recipientId = recipientId;
        this.retry = retry;
    }

    public int getStatus() {
        return status;
    }

    public int getRecipientId() {
        return recipientId;
    }

    public boolean isRetry() {
        return retry;
    }
}
