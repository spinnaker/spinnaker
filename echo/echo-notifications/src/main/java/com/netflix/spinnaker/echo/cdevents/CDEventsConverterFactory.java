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

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventSerializationException;
import io.cloudevents.jackson.JsonFormat;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public class CDEventsConverterFactory extends Converter.Factory {
  private final ObjectMapper objectMapper;
  private final JsonFormat jsonFormat;

  public CDEventsConverterFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.jsonFormat = new JsonFormat();
  }

  public static CDEventsConverterFactory create() {
    ObjectMapper objectMapper =
        EchoObjectMapper.getInstance()
            .rebuild()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();
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
          } catch (JacksonException e) {
            throw new IOException("Failed to serialize object to JSON", e);
          }
        };
  }

  public String convertCDEventToJson(CloudEvent cdEvent) {
    try {
      return new String(jsonFormat.serialize(cdEvent), StandardCharsets.UTF_8);
    } catch (EventSerializationException e) {
      throw new InvalidRequestException("Unable to convert CDEvent to Json format.", e);
    }
  }
}
