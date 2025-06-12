/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.metrics.ConversionException;
import com.netflix.kayenta.prometheus.model.PrometheusMetricDescriptorsResponse;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import retrofit2.Converter;
import retrofit2.Converter.Factory;
import retrofit2.Retrofit;

@Component
@Slf4j
public class PrometheusResponseConverter extends Factory {
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public PrometheusResponseConverter(ObjectMapper kayentaObjectMapper) {
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    if (type == PrometheusMetricDescriptorsResponse.class || type == String.class) {
      return new JacksonResponseConverter<>(kayentaObjectMapper, type);
    } else {
      return new PrometheusResultsConverter(kayentaObjectMapper);
    }
  }

  // Converter to handle Prometheus results conversion
  private static class PrometheusResultsConverter
      implements Converter<ResponseBody, List<PrometheusResults>> {
    private final ObjectMapper objectMapper;

    public PrometheusResultsConverter(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public List<PrometheusResults> convert(ResponseBody value) {
      try {
        Map responseMap = objectMapper.readValue(value.byteStream(), Map.class);
        Map data = (Map) responseMap.get("data");
        List<Map> resultList = (List<Map>) data.get("result");
        List<PrometheusResults> prometheusResultsList = new ArrayList<>(resultList.size());

        if (CollectionUtils.isEmpty(resultList)) {
          return null;
        }

        for (Map elem : resultList) {
          Map<String, String> tags = (Map<String, String>) elem.get("metric");
          String id = tags.remove("__name__");
          List<List> values = (List<List>) elem.get("values");
          List<Double> dataValues = new ArrayList<>(values.size());

          for (List tuple : values) {
            String val = (String) tuple.get(1);
            if (val != null) {
              switch (val) {
                case "+Inf":
                  dataValues.add(Double.POSITIVE_INFINITY);
                  break;
                case "-Inf":
                  dataValues.add(Double.NEGATIVE_INFINITY);
                  break;
                case "NaN":
                  dataValues.add(Double.NaN);
                  break;
                default:
                  dataValues.add(Double.valueOf(val));
              }
            }
          }

          long startTimeMillis =
              doubleTimestampSecsToLongTimestampMillis(values.get(0).get(0) + "");
          // If there aren't at least two data points, consider the step size to be zero.
          long stepSecs =
              values.size() > 1
                  ? TimeUnit.MILLISECONDS.toSeconds(
                      doubleTimestampSecsToLongTimestampMillis(values.get(1).get(0) + "")
                          - startTimeMillis)
                  : 0;
          long endTimeMillis = startTimeMillis + values.size() * stepSecs * 1000;

          prometheusResultsList.add(
              new PrometheusResults(
                  id, startTimeMillis, stepSecs, endTimeMillis, tags, dataValues));
        }

        return prometheusResultsList;
      } catch (IOException e) {
        throw new ConversionException("Failed to parse response from Prometheus", e);
      }
    }
  }

  // Simple converter for JSON responses using Jackson
  private static class JacksonResponseConverter<T> implements Converter<ResponseBody, T> {
    private final ObjectMapper objectMapper;
    private final Type type;

    JacksonResponseConverter(ObjectMapper objectMapper, Type type) {
      this.objectMapper = objectMapper;
      this.type = type;
    }

    @Override
    public T convert(ResponseBody value) throws IOException {
      try {
        if (type == String.class) {
          return (T) value.string();
        }
        return objectMapper.readValue(value.byteStream(), objectMapper.constructType(type));
      } finally {
        value.close();
      }
    }
  }

  private static long doubleTimestampSecsToLongTimestampMillis(String doubleTimestampSecsAsString) {
    return (long) (Double.parseDouble(doubleTimestampSecsAsString) * 1000);
  }
}
