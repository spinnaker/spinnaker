/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.retrofit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class CustomConverterFactoryTest {
  private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

  private final CustomConverterFactory factory =
      CustomConverterFactory.create(JsonMapper.builder().build());

  @SuppressWarnings("unchecked")
  @Test
  void convertsJackson3RequestsAndGenericResponses() throws IOException {
    Converter<Payload, RequestBody> requestConverter =
        (Converter<Payload, RequestBody>)
            factory.requestBodyConverter(
                Payload.class, NO_ANNOTATIONS, NO_ANNOTATIONS, (Retrofit) null);
    RequestBody requestBody = requestConverter.convert(new Payload("request"));

    Buffer requestBytes = new Buffer();
    requestBody.writeTo(requestBytes);

    Type responseType = new TypeReference<List<Payload>>() {}.getType();
    Converter<ResponseBody, ?> responseConverter =
        factory.responseBodyConverter(responseType, NO_ANNOTATIONS, null);
    List<Payload> response =
        (List<Payload>)
            responseConverter.convert(
                ResponseBody.create(
                    "[{\"value\":\"response\"}]", MediaType.get("application/json")));

    assertEquals(MediaType.get("application/json; charset=UTF-8"), requestBody.contentType());
    assertEquals("{\"value\":\"request\"}", requestBytes.readUtf8());
    assertEquals(List.of(new Payload("response")), response);
  }

  @Test
  void closesVoidResponses() throws IOException {
    TrackingResponseBody responseBody = new TrackingResponseBody();
    Converter<ResponseBody, ?> responseConverter =
        factory.responseBodyConverter(Void.class, NO_ANNOTATIONS, null);

    assertNull(responseConverter.convert(responseBody));
    assertTrue(responseBody.closed);
  }

  @Test
  void parsesJsonStringResponsesInStandardMode() {
    CustomConverterFactory standardFactory =
        CustomConverterFactory.createWithJsonStringResponses(JsonMapper.builder().build());
    Converter<ResponseBody, ?> responseConverter =
        standardFactory.responseBodyConverter(String.class, NO_ANNOTATIONS, null);

    assertThrows(
        JacksonException.class,
        () ->
            responseConverter.convert(
                ResponseBody.create(
                    "{\"value\":\"response\"}", MediaType.get("application/json"))));
  }

  private record Payload(String value) {}

  private static final class TrackingResponseBody extends ResponseBody {
    private boolean closed;

    @Override
    public MediaType contentType() {
      return MediaType.get("application/json");
    }

    @Override
    public long contentLength() {
      return 0;
    }

    @Override
    public BufferedSource source() {
      return new Buffer();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
