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

/**
 * A custom Converter.Factory for the Echo postEvent API.
 *
 * <p>This Factory is specifically designed to handle the conversion of Event objects for the Echo
 * API's postEvent method. The Event parameter is annotated with @Body and is an abstract class,
 * which caused the default Retrofit2 Jackson converter Factory to fail.
 *
 * <p>This Factory uses a custom ObjectMapper to serialize and deserialize Event objects, ensuring
 * compatibility with the Echo API.
 */
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

  /**
   * Returns a Retrofit2 Converter for the ResponseBody of the API call.
   *
   * <p>The Converter is responsible for converting the ResponseBody of the Retrofit2 API call into
   * an object of the specified type.
   *
   * <p>The Converter is used to handle the conversion of the response body of the API call to the
   * type specified by the type parameter.
   *
   * <p>If the type parameter is Void.class, the Converter simply closes the ResponseBody and
   * returns null.
   *
   * <p>Otherwise, the Converter uses the ObjectMapper to deserialize the response body into an
   * object of the specified type. This case doesn't arise but is included for completeness.
   *
   * <p>The Converter is used to handle the conversion of the response body of the API call to the
   * type specified by the type parameter.
   *
   * @param type the type of the object that the ResponseBody should be converted to
   * @param annotations the annotations on the Retrofit2 API call
   * @param retrofit the Retrofit2 instance
   * @return a Converter that can convert the ResponseBody of the API call to the specified type
   */
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

  /**
   * Returns a Retrofit2 Converter for the RequestBody of the API call.
   *
   * <p>The Converter is responsible for converting the RequestBody of the Retrofit2 API call into a
   * RequestBody object.
   *
   * <p>The Converter uses the ObjectMapper to serialize the object into a JSON byte array. It then
   * creates a RequestBody object from the JSON byte array and returns it.
   *
   * <p>The Converter is used to handle the conversion of the RequestBody of the API call to a
   * RequestBody object.
   *
   * @param type the type of the object that the RequestBody should be converted from
   * @param parameterAnnotations the annotations on the Retrofit2 API call parameter
   * @param methodAnnotations the annotations on the Retrofit2 API call method
   * @param retrofit the Retrofit2 instance
   * @return a Converter that can convert the request body of the API call of type `type` to a
   *     RequestBody object
   */
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
