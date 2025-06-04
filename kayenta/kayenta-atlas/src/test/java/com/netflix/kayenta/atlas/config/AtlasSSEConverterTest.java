package com.netflix.kayenta.atlas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.metrics.FatalQueryException;
import com.netflix.kayenta.metrics.RetryableQueryException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class AtlasSSEConverterTest {

  private String closeMessage = "data: { \"type\": \"close\" }\n";
  private String timeseriesMessage =
      "data: {\"type\":\"timeseries\",\"id\":\"randomIdHere\",\"query\":\"name,apache.http.request,:eq,statistic,count,:eq,:and,:sum,(,status,method,),:by\",\"tags\":{\"method\":\"get\",\"name\":\"apache.http.request\",\"statistic\":\"count\",\"atlas.offset\":\"0w\",\"status\":\"2xx\",\"nf.cluster\":\"foocluster\"},\"start\":1517860320000,\"end\":1517863920000,\"step\":60000,\"data\":{\"type\":\"array\",\"values\":[0.8666666666666667]}}\n";
  private String errorMessageIllegalStateMessage =
      "data: {\"type\":\"error\",\"message\":\"IllegalStateException: unknown word ':eqx'\"}\n";
  private String retryableErrorMessage =
      "data: {\"type\":\"error\",\"message\":\"something went wrong\"}\n";

  private List<AtlasResults> atlasResultsFromSSE(String sse) throws IOException {
    AtlasSSEConverter atlasSSEConverter = new AtlasSSEConverter(new ObjectMapper());
    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl("http://atlas")
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    Converter<ResponseBody, List<AtlasResults>> converter =
        atlasSSEConverter.responseBodyConverter(
            new ParameterizedTypeImpl(List.class, AtlasResults.class), new Annotation[0], retrofit);
    return converter.convert(ResponseBody.create(MediaType.parse("application/json"), sse));
  }

  @Test
  public void loneClose() throws IOException {
    List<AtlasResults> results = atlasResultsFromSSE(closeMessage);
    assertEquals(1, results.size());
  }

  @Test
  public void dataPlusClose() throws IOException {
    List<AtlasResults> results = atlasResultsFromSSE(timeseriesMessage + closeMessage);
    assertEquals(2, results.size());
  }

  @Test
  public void missingCloseThrows() {
    assertThrows(RetryableQueryException.class, () -> atlasResultsFromSSE(timeseriesMessage));
  }

  @Test
  public void fatalErrorWithoutClose() {
    assertThrows(
        FatalQueryException.class, () -> atlasResultsFromSSE(errorMessageIllegalStateMessage));
  }

  @Test
  public void retryableErrorWithoutClose() {
    assertThrows(RetryableQueryException.class, () -> atlasResultsFromSSE(retryableErrorMessage));
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
