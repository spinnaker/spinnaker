package com.netflix.spinnaker.orca.gremlin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.stream.Collectors;

public class GremlinConverter implements Converter {
  private static final String MIME_TYPE = "application/json; charset=UTF-8";

  private final ObjectMapper objectMapper;

  public GremlinConverter() {
    this(new ObjectMapper());
  }

  public GremlinConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override public Object fromBody(TypedInput body, Type type) throws ConversionException {
    try {
      if (type.getTypeName().equals(String.class.getName())) {
        return new BufferedReader(new InputStreamReader(body.in()))
          .lines().collect(Collectors.joining("\n"));
      } else {
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        return objectMapper.readValue(body.in(), javaType);
      }
    } catch (final IOException ioe) {
      throw new ConversionException(ioe);
    }
  }

  @Override public TypedOutput toBody(Object object) {
    try {
      String json = objectMapper.writeValueAsString(object);
      return new TypedByteArray(MIME_TYPE, json.getBytes("UTF-8"));
    } catch (JsonProcessingException | UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }
}
