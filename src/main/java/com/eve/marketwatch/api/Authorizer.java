package com.eve.marketwatch.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eve.marketwatch.service.SecurityService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Authorizer implements RequestHandler<TokenAuthorizerContext, AuthPolicy> {

    private static final Logger LOG = LogManager.getLogger(Authorizer.class);
    private final SecurityService securityService = new SecurityService();

    @Override
    public AuthPolicy handleRequest(TokenAuthorizerContext input, Context context) {
        final String token = input.getAuthorizationToken();

        LOG.info("Attempting authorization with token " + token);

        // if the token is valid, a policy should be generated which will allow or deny access to the client

        // if access is denied, the client will receive a 403 Access Denied response
        // if access is allowed, API Gateway will proceed with the back-end integration configured on the method that was called

        String methodArn = input.getMethodArn();
        LOG.info(methodArn);
        String[] arnPartials = methodArn.split(":");
        String region = arnPartials[3];
        String awsAccountId = arnPartials[4];
        String[] apiGatewayArnPartials = arnPartials[5].split("/");
        String restApiId = apiGatewayArnPartials[0];
        String stage = apiGatewayArnPartials[1];
        String resource = ""; // root resource
        if (apiGatewayArnPartials.length >= 4) {
            resource = methodArn;
        }

        // validate the incoming token
        // and produce the principal user identifier associated with the token
        final int characterId = securityService.getCharacterId(token).orElseThrow(() -> new RuntimeException("Unauthorized"));

        LOG.info("Authorizing characterId " + characterId);

        final String adminCharacterId = System.getenv("ADMIN_CHARACTER_ID");
        if (adminCharacterId != null && characterId != Integer.parseInt(adminCharacterId)) {
            LOG.warn("Forbidden access for characterId " + characterId);
            throw new RuntimeException("Forbidden");
        }

        // this function must generate a policy that is associated with the recognized principal user identifier.
        // depending on your use case, you might store policies in a DB, or generate them on the fly

        // keep in mind, the policy is cached for 5 minutes by default (TTL is configurable in the authorizer)
        // and will apply to subsequent calls to any method/resource in the RestApi
        // made with the same token

        LOG.info("Successfully authorized characterId " + characterId + " for resource " + resource);

        return new AuthPolicy(String.valueOf(characterId),
                AuthPolicy.PolicyDocument.allowOneArn(region, awsAccountId, restApiId, stage, resource));
    }

}
