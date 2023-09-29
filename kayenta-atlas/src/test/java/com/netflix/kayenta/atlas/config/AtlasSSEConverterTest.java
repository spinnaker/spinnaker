package com.netflix.kayenta.atlas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.metrics.FatalQueryException;
import com.netflix.kayenta.metrics.RetryableQueryException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AtlasSSEConverterTest {

  private String closeMessage = "data: { \"type\": \"close\" }\n";
  private String timeseriesMessage =
      "data: {\"type\":\"timeseries\",\"id\":\"randomIdHere\",\"query\":\"name,apache.http.request,:eq,statistic,count,:eq,:and,:sum,(,status,method,),:by\",\"tags\":{\"method\":\"get\",\"name\":\"apache.http.request\",\"statistic\":\"count\",\"atlas.offset\":\"0w\",\"status\":\"2xx\",\"nf.cluster\":\"foocluster\"},\"start\":1517860320000,\"end\":1517863920000,\"step\":60000,\"data\":{\"type\":\"array\",\"values\":[0.8666666666666667]}}\n";
  private String errorMessageIllegalStateMessage =
      "data: {\"type\":\"error\",\"message\":\"IllegalStateException: unknown word ':eqx'\"}\n";
  private String retryableErrorMessage =
      "data: {\"type\":\"error\",\"message\":\"something went wrong\"}\n";

  private List<AtlasResults> atlasResultsFromSSE(String sse) {
    BufferedReader bufferedReader = new BufferedReader(new StringReader(sse));
    AtlasSSEConverter atlasSSEConverter = new AtlasSSEConverter(new ObjectMapper());
    return atlasSSEConverter.processInput(bufferedReader);
  }

  @Test
  public void loneClose() {
    List<AtlasResults> results = atlasResultsFromSSE(closeMessage);
    assertEquals(1, results.size());
  }

  @Test
  public void dataPlusClose() {
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
}
