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

package com.netflix.kayenta.influxdb.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.netflix.kayenta.canary.providers.metrics.InfluxdbCanaryMetricSetQueryConfig;
import com.netflix.kayenta.influxdb.canary.InfluxDbCanaryScope;
import com.netflix.kayenta.influxdb.metrics.InfluxDbQueryBuilder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class InfluxdbQueryBuilderTest {

  private InfluxDbQueryBuilder queryBuilder = new InfluxDbQueryBuilder();

  @Test
  public void testBuild_noScope() {
    String measurement = "temperature";

    InfluxDbCanaryScope canaryScope = createScope();
    InfluxdbCanaryMetricSetQueryConfig queryConfig = queryConfig(measurement, fieldsList());
    String query = queryBuilder.build(queryConfig, canaryScope);
    assertThat(
        query,
        is(
            "SELECT external, internal FROM temperature WHERE time >= '2010-01-01T12:00:00Z' AND time < '2010-01-01T12:01:40Z'"));
  }

  private InfluxDbCanaryScope createScope() {
    InfluxDbCanaryScope canaryScope = new InfluxDbCanaryScope();
    canaryScope.setStart(Instant.ofEpochSecond(1262347200));
    canaryScope.setEnd(Instant.ofEpochSecond(1262347300));
    return canaryScope;
  }

  private List<String> fieldsList() {
    List<String> fields = new ArrayList<>();
    fields.add("external");
    fields.add("internal");
    return fields;
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBuild_withInvalidScope() {
    String measurement = "temperature";

    InfluxDbCanaryScope canaryScope = createScope();
    canaryScope.setScope("server='myapp-prod-v002'");
    InfluxdbCanaryMetricSetQueryConfig queryConfig = queryConfig(measurement, fieldsList());
    queryBuilder.build(queryConfig, canaryScope);
  }

  @Test
  public void testBuild_withValidScope() {
    String measurement = "temperature";

    InfluxDbCanaryScope canaryScope = createScope();
    canaryScope.setScope("server:myapp-prod-v002");
    InfluxdbCanaryMetricSetQueryConfig queryConfig = queryConfig(measurement, fieldsList());
    String query = queryBuilder.build(queryConfig, canaryScope);
    assertThat(
        query,
        is(
            "SELECT external, internal FROM temperature WHERE time >= '2010-01-01T12:00:00Z' AND time < '2010-01-01T12:01:40Z' AND server='myapp-prod-v002'"));
  }

  private InfluxdbCanaryMetricSetQueryConfig queryConfig(
      String measurement, List<String> fieldsList) {
    InfluxdbCanaryMetricSetQueryConfig queryConfig =
        InfluxdbCanaryMetricSetQueryConfig.builder()
            .metricName(measurement)
            .fields(fieldsList)
            .build();
    return queryConfig;
  }

  @Test
  public void testBuild_withNoFieldsSpecified() {
    String measurement = "temperature";

    InfluxDbCanaryScope canaryScope = createScope();
    canaryScope.setScope("server:myapp-prod-v002");
    InfluxdbCanaryMetricSetQueryConfig queryConfig = queryConfig(measurement, null);
    String query = queryBuilder.build(queryConfig, canaryScope);
    assertThat(
        query,
        is(
            "SELECT *::field FROM temperature WHERE time >= '2010-01-01T12:00:00Z' AND time < '2010-01-01T12:01:40Z' AND server='myapp-prod-v002'"));
  }
}
