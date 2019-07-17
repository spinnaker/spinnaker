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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.influxdb.model.InfluxDbResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

@Component
@Slf4j
public class InfluxDbResponseConverter implements Converter {

  private static final int DEFAULT_STEP_SIZE = 0;
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public InfluxDbResponseConverter(ObjectMapper kayentaObjectMapper) {
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Override
  public Object fromBody(TypedInput body, Type type) throws ConversionException {

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.in()))) {
      String json = reader.readLine();
      log.debug("Converting response from influxDb: {}", json);

      Map result = getResultObject(json);
      List<Map> seriesList = (List<Map>) result.get("series");

      if (CollectionUtils.isEmpty(seriesList)) {
        log.warn("Received no data from Influxdb.");
        return null;
      }

      Map series = seriesList.get(0);
      List<String> seriesColumns = (List<String>) series.get("columns");
      List<List> seriesValues = (List<List>) series.get("values");
      List<InfluxDbResult> influxDbResultsList = new ArrayList<InfluxDbResult>(seriesValues.size());

      // TODO(joerajeev): if returning tags (other than the field names) we will need to skip tags
      // from this loop,
      // and to extract and set the tag values to the influxDb result.
      for (int i = 1;
          i < seriesColumns.size();
          i++) { // Starting from index 1 to skip 'time' column

        String id = seriesColumns.get(i);
        long firstTimeMillis = extractTimeInMillis(seriesValues, 0);
        long stepMillis = calculateStep(seriesValues, firstTimeMillis);
        List<Double> values = new ArrayList<>(seriesValues.size());
        for (List<Object> valueRow : seriesValues) {
          if (valueRow.get(i) != null) {
            values.add(Double.valueOf((Integer) valueRow.get(i)));
          }
        }
        influxDbResultsList.add(new InfluxDbResult(id, firstTimeMillis, stepMillis, null, values));
      }

      log.debug("Converted response: {} ", influxDbResultsList);
      return influxDbResultsList;
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private Map getResultObject(String json)
      throws IOException, JsonParseException, JsonMappingException, ConversionException {
    Map responseMap = kayentaObjectMapper.readValue(json, Map.class);
    List<Map> results = (List<Map>) responseMap.get("results");
    if (CollectionUtils.isEmpty(results)) {
      throw new ConversionException("Unexpected response from influxDb");
    }
    Map result = (Map) results.get(0);
    return result;
  }

  private long calculateStep(List<List> seriesValues, long firstTimeMillis) {
    long nextTimeMillis = extractTimeInMillis(seriesValues, 1);
    long stepMillis =
        seriesValues.size() > 1 ? nextTimeMillis - firstTimeMillis : DEFAULT_STEP_SIZE;
    return stepMillis;
  }

  private long extractTimeInMillis(List<List> seriesValues, int index) {
    String firstUtcTime = (String) seriesValues.get(index).get(0);
    long startTimeMillis = Instant.parse(firstUtcTime).toEpochMilli();
    return startTimeMillis;
  }

  @Override
  public TypedOutput toBody(Object object) {
    return null;
  }
}
