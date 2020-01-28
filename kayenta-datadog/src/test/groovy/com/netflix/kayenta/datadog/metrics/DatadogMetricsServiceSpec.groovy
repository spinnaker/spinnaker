/*
 * Copyright 2020 Zendesk Inc.
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

package com.netflix.kayenta.datadog.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricConfig
import com.netflix.kayenta.canary.providers.metrics.DatadogCanaryMetricSetQueryConfig
import com.netflix.kayenta.datadog.canary.DatadogCanaryScope
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DatadogMetricsServiceSpec extends Specification {

  @Shared
  DatadogMetricsService datadogMetricsService = DatadogMetricsService.builder().build()

  @Unroll
  void "Can dry-run query generation"() {
    given:
    DatadogCanaryMetricSetQueryConfig queryConfig =
      DatadogCanaryMetricSetQueryConfig.builder()
        .metricName(metricName)
        .customInlineTemplate(customInlineTemplate)
        .build()
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig.builder()
        .query(queryConfig)
        .build()
    CanaryConfig canaryConfig =
      CanaryConfig.builder()
        .metric(canaryMetricConfig)
        .build()
    DatadogCanaryScope datadogCanaryScope =
      new DatadogCanaryScope()
        .setScope(scope)

    when:
    String query = datadogMetricsService.buildQuery(null, canaryConfig, canaryMetricConfig, datadogCanaryScope)

    then:
    query == expectedQuery

    where:
    metricName         | customInlineTemplate                                     | scope          | extendedScopeParams || expectedQuery
    "sum:app.errors"   | null                                                     | "tag:some-tag" | null                || 'sum:app.errors{tag:some-tag}'
    null               | 'sum:app.errors{${scope}}/sum:app.requests{${scope}}'    | "tag:some-tag" | null                || 'sum:app.errors{tag:some-tag}/sum:app.requests{tag:some-tag}'
  }
}
