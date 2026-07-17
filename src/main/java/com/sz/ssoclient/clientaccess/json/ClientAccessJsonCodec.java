package com.sz.ssoclient.clientaccess.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/** Dedicated Jackson 2 codec for the CAP V1 wire contract. */
public final class ClientAccessJsonCodec {

    private final ObjectMapper objectMapper;

    public ClientAccessJsonCodec() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public ClientAccessJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(value);
    }

    public <T> T readValue(byte[] value, Class<T> type) throws IOException {
        return objectMapper.readValue(value, type);
    }

    public <T> T readValue(byte[] value, Class<?> rawType, Class<?> parameterType) throws IOException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(rawType, parameterType);
        return objectMapper.readValue(value, type);
    }
}
