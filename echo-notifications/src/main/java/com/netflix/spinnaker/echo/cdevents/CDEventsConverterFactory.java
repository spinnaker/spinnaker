/*
 * Copyright 2024 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.cdevents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class CDEventsConverterFactory extends Converter.Factory {
  private final ObjectMapper objectMapper;

  public CDEventsConverterFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static CDEventsConverterFactory create() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(JsonFormat.getCloudEventJacksonModule());
    objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    return new CDEventsConverterFactory(objectMapper);
  }

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    return (Converter<ResponseBody, Object>)
        value -> {
          try (value) {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.readValue(value.charStream(), javaType);
          }
        };
  }

  @Override
  public Converter<?, RequestBody> requestBodyConverter(
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations,
      Retrofit retrofit) {
    return (Converter<Object, RequestBody>)
        value -> {
          try {
            String json = objectMapper.writeValueAsString(value);
            return RequestBody.create(MediaType.parse("application/json"), json);
          } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialize object to JSON", e);
          }
        };
  }

  public String convertCDEventToJson(CloudEvent cdEvent) {
    try {
      return objectMapper.writeValueAsString(cdEvent);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Unable to convert CDEvent to Json format.", e);
    }
  }
}
