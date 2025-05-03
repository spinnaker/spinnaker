/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.igor.history;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public class EchoConverterFactory extends Converter.Factory {
  private final ObjectMapper mapper;
  private static final MediaType DEFAULT_MEDIA_TYPE =
      MediaType.get("application/json; charset=UTF-8");

  public static EchoConverterFactory create() {
    return new EchoConverterFactory(new ObjectMapper());
  }

  private EchoConverterFactory(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    if (type == Void.class) {
      return (Converter<ResponseBody, Void>)
          value -> {
            value.close();
            return null;
          };
    }
    return (Converter<ResponseBody, Object>)
        value -> {
          JavaType javaType = mapper.getTypeFactory().constructType(type);
          return mapper.readValue(value.charStream(), javaType);
        };
  }

  public Converter<?, RequestBody> requestBodyConverter(
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations,
      Retrofit retrofit) {
    return (Converter<Object, RequestBody>)
        value -> {
          byte[] jsonValue = mapper.writeValueAsBytes(value);
          return RequestBody.create(jsonValue, DEFAULT_MEDIA_TYPE);
        };
  }
}
