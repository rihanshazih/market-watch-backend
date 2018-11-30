package com.eve.marketwatch.service;

import com.eve.marketwatch.exceptions.BadRequestException;
import com.eve.marketwatch.model.eveauth.AccessTokenResponse;
import com.eve.marketwatch.model.eveauth.AuthVerificationResponse;
import com.eve.marketwatch.model.eveauth.CharacterDetailsResponse;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EveAuthService {

    private static final Logger LOG = LogManager.getLogger(EveAuthService.class);
    private static final String CLIENT_ID = System.getenv("APP_CLIENT_ID");
    private static final String CLIENT_SECRET = System.getenv("APP_CLIENT_SECRET");

    private final javax.ws.rs.client.Client webClient = ClientBuilder.newClient();

    public AccessTokenResponse generateAccessToken(final String refreshToken) throws BadRequestException {
        return generateAccessToken(refreshToken, CLIENT_ID, CLIENT_SECRET);
    }

    public AccessTokenResponse generateAccessToken(final String refreshToken, final String clientId, final String clientSecret) throws BadRequestException {
        final Response response = webClient.target("https://login.eveonline.com/oauth/token").request()
                .header("Authorization", "Basic " + base64Encode(clientId, clientSecret))
                .post(Entity.entity("grant_type=refresh_token&refresh_token=" + refreshToken, "application/x-www-form-urlencoded"));
        LOG.info("Access token response code was " + response.getStatus());
        final String json = response.readEntity(String.class);
        LOG.info(json);
        if (response.getStatus() == 200) {
            return new GsonBuilder().create().fromJson(json, AccessTokenResponse.class);
        } else {
            LOG.warn("Failed to get access token: " + response.getStatus());
            throw new BadRequestException("Failed to retrieve access token." + json);
        }
    }

    public static String base64Encode(final String a, final String b) {
        return Base64.getEncoder().encodeToString((a + ":" + b).getBytes(StandardCharsets.UTF_8));
    }

    public CharacterDetailsResponse getCharacterDetails(final String accessToken) {
        final Response response = webClient.target("https://login.eveonline.com/oauth/verify")
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get();
        LOG.info("Verify response code was " + response.getStatus());
        final String json = response.readEntity(String.class);
        LOG.info(json);
        return new GsonBuilder().create().fromJson(json, CharacterDetailsResponse.class);
    }

    public AuthVerificationResponse verifyAuthentication(final String code) {
        final Response response = webClient.target("https://login.eveonline.com/oauth/token")
                .request()
                .header("Authorization", "Basic " + base64Encode(CLIENT_ID, CLIENT_SECRET))
                .post(Entity.entity("grant_type=authorization_code&code=" + code, "application/x-www-form-urlencoded"));
        LOG.info("Auth response code was " + response.getStatus());
        if (response.getStatus() != 200) {
            LOG.info(code);
        }
        final String json = response.readEntity(String.class);
        LOG.info(json);
        return new GsonBuilder().create().fromJson(json, AuthVerificationResponse.class);
    }
}
