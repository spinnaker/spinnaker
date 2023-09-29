/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.kayenta.datadog.service;

import static com.netflix.kayenta.datadog.service.DatadogTimeSeries.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class DatadogTimeSeriesTest {

  private static final long START = 1620785040000L;
  private static final long INTERVAL_MILLIS = 5000L;

  @Test
  public void fillsInSparseValuesWithNaN() {
    DatadogSeriesEntry series =
        new DatadogSeriesEntry()
            .setInterval(INTERVAL_MILLIS / 1000)
            .setStart(START)
            .setEnd(START + 10 * INTERVAL_MILLIS)
            .setPointlist(
                List.of(
                    Arrays.asList(START + 0 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 2 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 3 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 4 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 10 * INTERVAL_MILLIS, 1)));

    List<Double> expected =
        List.of(
            1.0,
            Double.NaN,
            1.0,
            1.0,
            1.0,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            1.0);
    assertEquals(expected, series.getDataPoints().collect(Collectors.toList()));
  }

  @Test
  public void handlesNullInPointlist() {
    DatadogSeriesEntry series =
        new DatadogSeriesEntry()
            .setInterval(INTERVAL_MILLIS / 1000)
            .setStart(START)
            .setEnd(START + 10 * INTERVAL_MILLIS)
            .setPointlist(
                List.of(
                    Arrays.asList(START + 0 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 1 * INTERVAL_MILLIS, null),
                    Arrays.asList(START + 2 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 3 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 4 * INTERVAL_MILLIS, 1),
                    Arrays.asList(START + 10 * INTERVAL_MILLIS, 1)));

    List<Double> expected =
        List.of(
            1.0,
            Double.NaN,
            1.0,
            1.0,
            1.0,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            1.0);
    assertEquals(expected, series.getDataPoints().collect(Collectors.toList()));
  }
}
