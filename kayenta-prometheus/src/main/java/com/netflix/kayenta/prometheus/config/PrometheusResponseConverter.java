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
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PrometheusResponseConverter implements Converter {

  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public PrometheusResponseConverter(ObjectMapper kayentaObjectMapper) {
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Override
  public List<PrometheusResults> fromBody(TypedInput body, Type type) throws ConversionException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.in()))) {
      String json = reader.readLine();
      Map responseMap = kayentaObjectMapper.readValue(json, Map.class);
      Map data = (Map)responseMap.get("data");
      List<Map> resultList = (List<Map>)data.get("result");
      if (resultList == null || resultList.isEmpty()) {
        log.error("Received no data from Prometheus.");
        return null;
      }

      List<PrometheusResults> prometheusResultsList = new ArrayList<PrometheusResults>(resultList.size());
      for (Map elem : resultList) {
          Map<String, String> tags = (Map<String, String>)elem.get("metric");
          String id = tags.get("__name__");
          String query = id; // !!! I dont know the original query
          tags.remove("__name__");

          List<List> values = (List<List>)elem.get("values");
          List<Double> dataValues = new ArrayList<Double>(values.size());
          for (List tuple : values) {
              dataValues.add(Double.valueOf((String)tuple.get(1)));
          }

          long startSecs = ((Integer)values.get(0).get(0)).longValue();
          long stepSecs = ((Integer)values.get(1).get(0)).longValue() - startSecs;
          long endSecs = startSecs + values.size() * stepSecs;
          prometheusResultsList.add(
              new PrometheusResults(id, query,
                                    startSecs, stepSecs, endSecs,
                                    tags, dataValues));
      }

      return prometheusResultsList;
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  public TypedOutput toBody(Object object) {
    return null;
  }
}
