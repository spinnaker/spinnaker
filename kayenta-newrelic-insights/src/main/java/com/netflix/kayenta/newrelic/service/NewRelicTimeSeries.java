/*
 * Copyright 2018 Adobe
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

package com.netflix.kayenta.newrelic.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class NewRelicTimeSeries {

  private List<NewRelicSeriesEntry> timeSeries;
  private NewresultResultMetadata metadata;

  @Data
  static public class NewRelicSeriesEntry {

    private Long beginTimeSeconds;
    private Long inspectedCount;
    private Long endTimeSeconds;
    private List<HashMap<String, Number>> results;

    @JsonIgnore
    private Double adjustSingleResult(HashMap<String, Number> entry) {
      final Number num = entry.values().iterator().next();
      if (inspectedCount == 0L || num == null) {
        return Double.NaN;
      } else {
        return num.doubleValue();
      }
    }

    @JsonIgnore
    public Double getValue() {
      return adjustSingleResult(results.get(0));
    }
  }

  public Stream<Double> getDataPoints() {
    return timeSeries.stream().map(o -> o.getValue());
  }

  @Data
  static public class NewresultResultMetadata {

    private Long beginTimeMillis;
    private Long endTimeMillis;
  }
}
