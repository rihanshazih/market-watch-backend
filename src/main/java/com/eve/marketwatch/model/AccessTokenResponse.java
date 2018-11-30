package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class AccessTokenResponse {
    // todo: merge this with AuthVerificationResponse if refresh_token is present on both
    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("expires_in")
    private int expiresIn;

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
