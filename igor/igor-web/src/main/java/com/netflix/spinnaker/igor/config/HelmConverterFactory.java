/*
 * Copyright 2025 The Home Depot, Inc.
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

package com.netflix.spinnaker.igor.config;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Converter.Factory;
import retrofit2.Retrofit;

// Custom converter to deal with index file raw string responses
public class HelmConverterFactory extends Factory {
  private static final MediaType DEFAULT_MEDIA_TYPE =
      MediaType.get("application/json; charset=UTF-8");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Converter<?, RequestBody> requestBodyConverter(
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations,
      Retrofit retrofit) {
    return (Converter<Object, RequestBody>)
        value -> {
          byte[] jsonValue = objectMapper.writeValueAsBytes(value);
          return RequestBody.create(jsonValue, DEFAULT_MEDIA_TYPE);
        };
  }

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    // If the return type is a String, provide it as such
    if (type.getTypeName().equals("java.lang.String")) {
      return (Converter<ResponseBody, String>) value -> IOUtils.toString(value.byteStream());
    }

    return (Converter<ResponseBody, Object>)
        value -> {
          JavaType javaType = objectMapper.getTypeFactory().constructType(type);
          return objectMapper.readValue(value.charStream(), javaType);
        };
  }
}
