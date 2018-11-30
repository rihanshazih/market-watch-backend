package com.eve.marketwatch;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class ItemSearchHandlerTest {

    private final ItemSearchHandler sut = new ItemSearchHandler();

    @Test
    void handleRequest() {
        final Map<String, Object> input = new HashMap<>();
        final Map<String, String> map = new HashMap<>();
        map.put("term", "Inertia");
        input.put("queryStringParameters", map);

        final ApiGatewayResponse response = sut.handleRequest(input, null);

        System.out.println(response.getBody());
    }
}
