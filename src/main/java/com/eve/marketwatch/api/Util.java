package com.eve.marketwatch.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;

public class Util {

    private static final Logger LOG = LogManager.getLogger(Util.class);

    public static <T> T parseBody(Class<T> clazz, Map<String, Object> input) {
        try {
            return new ObjectMapper().readValue(input.get("body").toString(), clazz);
        } catch (IOException e) {
            LOG.error("Failed to parse body " + input.get("body").toString(), e);
            throw new RuntimeException("Failed to parse body " + input.get("body").toString());
        }
    }
}
