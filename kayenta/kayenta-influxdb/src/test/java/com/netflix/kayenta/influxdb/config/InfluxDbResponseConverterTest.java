/*
 * Copyright 2018 Joseph Motha
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.influxdb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.influxdb.model.InfluxDbResult;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class InfluxDbResponseConverterTest {

  private static final String MIME_TYPE = "application/json; charset=UTF-8";
  private final String EXAMPLE_ALL_INTEGERS =
      "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"temperature\",\"columns\":[\"time\",\"external\",\"internal\"],\"values\":[[\"2018-05-27T04:50:44.105612486Z\",25,37],[\"2018-05-27T04:51:44.105612486Z\",25,37],[\"2018-05-27T04:52:06.585796188Z\",26,38]]}]}]}";

  private final String EXAMPLE_WITH_FLOATS =
      "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"temperature\",\"columns\":[\"time\",\"external\",\"internal\"],\"values\":[[\"2018-05-27T04:50:44.105612486Z\",25.1,37.1],[\"2018-05-27T04:51:44.105612486Z\",25,37.2],[\"2018-05-27T04:52:06.585796188Z\",26.5,38]]}]}]}";

  public List<InfluxDbResult> setupAllIntegers() {
    List<InfluxDbResult> results = new ArrayList<>();
    List<Double> externalDataValues = new ArrayList<>();
    externalDataValues.add(25d);
    externalDataValues.add(25d);
    externalDataValues.add(26d);
    InfluxDbResult externalTempResult =
        new InfluxDbResult("external", 1527396644105L, 60000L, null, externalDataValues);
    results.add(externalTempResult);

    List<Double> internalDataValues = new ArrayList<>();
    internalDataValues.add(37d);
    internalDataValues.add(37d);
    internalDataValues.add(38d);
    InfluxDbResult internalTempResult =
        new InfluxDbResult("internal", 1527396644105L, 60000L, null, internalDataValues);
    results.add(internalTempResult);
    return results;
  }

  public List<InfluxDbResult> setupWithFloats() {
    List<InfluxDbResult> results = new ArrayList<>();
    List<Double> externalDataValues = new ArrayList<>();
    externalDataValues.add(25.1);
    externalDataValues.add(25d);
    externalDataValues.add(26.5);
    InfluxDbResult externalTempResult =
        new InfluxDbResult("external", 1527396644105L, 60000L, null, externalDataValues);
    results.add(externalTempResult);

    List<Double> internalDataValues = new ArrayList<>();
    internalDataValues.add(37.1);
    internalDataValues.add(37.2);
    internalDataValues.add(38d);
    InfluxDbResult internalTempResult =
        new InfluxDbResult("internal", 1527396644105L, 60000L, null, internalDataValues);
    results.add(internalTempResult);
    return results;
  }

  Retrofit retrofit =
      new Retrofit.Builder()
          .baseUrl("http://influxdb")
          .addConverterFactory(JacksonConverterFactory.create())
          .build();

  private final InfluxDbResponseConverter influxDbResponseConverter =
      new InfluxDbResponseConverter(new ObjectMapper());

  Converter<ResponseBody, List<InfluxDbResult>> converter =
      (Converter<ResponseBody, List<InfluxDbResult>>)
          influxDbResponseConverter.responseBodyConverter(
              new ParameterizedTypeImpl(List.class, InfluxDbResult.class),
              new Annotation[0],
              retrofit);

  @Test
  public void serialize_shouldNotSupportRequestConversion() {
    // The converter doesn't support request body conversion
    assertThat(
            influxDbResponseConverter.requestBodyConverter(
                List.class, new Annotation[0], new Annotation[0], retrofit))
        .isNull();
  }

  @Test
  public void deserialize() throws Exception {
    List<InfluxDbResult> expectedResults = setupAllIntegers();
    ResponseBody responseBody =
        ResponseBody.create(okhttp3.MediaType.parse(MIME_TYPE), EXAMPLE_ALL_INTEGERS.getBytes());
    List<InfluxDbResult> result = converter.convert(responseBody);

    assertThat(result).isEqualTo(expectedResults);
  }

  @Test
  public void deserializeWithFloatValues() throws Exception {
    List<InfluxDbResult> expectedResults = setupWithFloats();
    ResponseBody responseBody =
        ResponseBody.create(okhttp3.MediaType.parse(MIME_TYPE), EXAMPLE_WITH_FLOATS.getBytes());
    List<InfluxDbResult> result = converter.convert(responseBody);

    assertThat(result).isEqualTo(expectedResults);
  }

  @Test
  public void deserializeWrongValue() {
    ResponseBody responseBody =
        ResponseBody.create(okhttp3.MediaType.parse(MIME_TYPE), "{\"foo\":\"bar\"}".getBytes());
    assertThrows(IOException.class, () -> converter.convert(responseBody));
  }

  private static class ParameterizedTypeImpl implements ParameterizedType {
    private final Type rawType;
    private final Type[] typeArguments;

    public ParameterizedTypeImpl(Type rawType, Type... typeArguments) {
      this.rawType = rawType;
      this.typeArguments = typeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return typeArguments;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }
  }
}
