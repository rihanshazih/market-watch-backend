package com.eve.marketwatch.api;

import java.util.HashMap;
import java.util.Map;

final class InputExtractor {

    private InputExtractor() {
    }

    static String getPathParam(String param, Map<String, Object> input) {
        final HashMap pathParameters = (HashMap) input.get("pathParameters");
        return (String) pathParameters.get(param);
    }

    static String getQueryParam(String param, Map<String, Object> input) {
        final HashMap queryParameters = (HashMap) input.get("queryStringParameters");
        if (null == queryParameters) {
            return null;
        }
        return (String) queryParameters.get(param);
    }

    static Integer getCharacterId(Map<String, Object> input) {
        final HashMap authorizerParams = (HashMap) ((HashMap) input.get("requestContext")).get("authorizer");
        if (null == authorizerParams) {
            throw new RuntimeException("Missing authorizer info.");
        }
        return Integer.parseInt(authorizerParams.get("principalId").toString());
    }

}
