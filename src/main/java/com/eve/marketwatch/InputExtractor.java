package com.eve.marketwatch;

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

}
