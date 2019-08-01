/*
 * Copyright 2019 Playtika
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
package com.netflix.kayenta.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.providers.metrics.GraphiteCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.PrometheusCanaryMetricSetQueryConfig;
import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CanaryConfigReader {

  private static ObjectMapper objectMapper = getObjectMapper();

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerSubtypes(PrometheusCanaryMetricSetQueryConfig.class);
    objectMapper.registerSubtypes(GraphiteCanaryMetricSetQueryConfig.class);
    return objectMapper;
  }

  public static CanaryConfig getCanaryConfig(String name) {
    try {
      return objectMapper.readValue(
          CanaryConfigReader.class.getClassLoader().getResourceAsStream(name), CanaryConfig.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get canary config file: " + name, e);
    }
  }
}
