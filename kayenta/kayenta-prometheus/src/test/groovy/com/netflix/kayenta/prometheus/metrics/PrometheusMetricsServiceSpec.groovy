/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.kayenta.prometheus.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricConfig
import com.netflix.kayenta.canary.providers.metrics.PrometheusCanaryMetricSetQueryConfig
import com.netflix.kayenta.prometheus.canary.PrometheusCanaryScope
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class PrometheusMetricsServiceSpec extends Specification {

  @Shared
  Map<String, String> templates = [
    someTemplate: 'some-label=${scope}',
    anotherTemplate: 'my-server-group=${scope}'
  ]

  @Shared
  PrometheusMetricsService prometheusMetricsService = PrometheusMetricsService.builder().scopeLabel("instance").build()

  @Unroll
  void "Can dry-run query generation"() {
    given:
    PrometheusCanaryMetricSetQueryConfig queryConfig =
      PrometheusCanaryMetricSetQueryConfig.builder()
        .resourceType(resourceType)
        .metricName("some-metric-name")
        .labelBindings(labelBindings)
        .customFilterTemplate(customFilterTemplate)
        .customInlineTemplate(customInlineTemplate)
        .build()
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig.builder()
        .query(queryConfig)
        .build()
    CanaryConfig canaryConfig =
      CanaryConfig.builder()
        .templates(templates)
        .metric(canaryMetricConfig)
        .build()
    PrometheusCanaryScope prometheusCanaryScope =
      new PrometheusCanaryScope()
        .setScope(scope)
        .setLocation("us-central1")
        .setExtendedScopeParams(extendedScopeParams)

    when:
    String query = prometheusMetricsService.buildQuery(null, canaryConfig, canaryMetricConfig, prometheusCanaryScope)

    then:
    query == expectedQuery

    where:
    labelBindings | customFilterTemplate         | customInlineTemplate                                                                                                               | resourceType       | scope               | extendedScopeParams || expectedQuery
    // Rely on paved-road composition of queries.
    ["a=b"]       | null                         | null                                                                                                                               | "gce_instance"     | "some-server-group" | null                || 'avg(some-metric-name{a=b,instance=~"some-server-group-.{4}",zone=~".+/zones/us-central1-.{1}"})'
    ["a=b"]       | null                         | ""                                                                                                                                 | "gce_instance"     | "some-server-group" | null                || 'avg(some-metric-name{a=b,instance=~"some-server-group-.{4}\",zone=~".+/zones/us-central1-.{1}"})'
    ["a=b"]       | null                         | null                                                                                                                               | "aws_ec2_instance" | "some-server-group" | null                || 'avg(some-metric-name{a=b,asg_groupName="some-server-group",zone=~"us-central1.{1}"})'
    ["a=b"]       | null                         | ""                                                                                                                                 | "aws_ec2_instance" | "some-server-group" | null                || 'avg(some-metric-name{a=b,asg_groupName="some-server-group",zone=~"us-central1.{1}"})'

    // Rely on custom filters and custom filter templates.
    ["a=b"]       | "someTemplate"               | null                                                                                                                               | "gce_instance"     | "some-app-canary"   | null                || "avg(some-metric-name{some-label=some-app-canary,a=b})"
    ["a=b"]       | "someTemplate"               | "customFilter=something,andSomething=else"                                                                                         | "gce_instance"     | null                | null                || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                         | "customFilter=something,andSomething=else"                                                                                         | "gce_instance"     | null                | null                || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                         | "customFilter=something,andSomething=else"                                                                                         | "aws_ec2_instance" | null                | null                || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                         | "customFilter=something,andSomething=else"                                                                                         | "anything_else"    | null                | null                || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | "anotherTemplate"            | null                                                                                                                               | null               | "some-baseline"     | null                || "avg(some-metric-name{my-server-group=some-baseline,a=b})"
    ["a=b"]       | "anotherTemplate"            | null                                                                                                                               | ""                 | "some-canary"       | null                || "avg(some-metric-name{my-server-group=some-canary,a=b})"

    // Provide fully-specified PromQl expressions (including template expansion).
    null          | null                         | 'PromQL:histogram_quantile(0.5, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="/graph"})'           | null               | null                | null                || 'histogram_quantile(0.5, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="/graph"})'
    null          | null                         | 'PromQL:histogram_quantile(0.5, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="${scope}"})'         | null               | "/graph"            | null                || 'histogram_quantile(0.5, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="/graph"})'
    null          | null                         | 'PromQL:histogram_quantile(${quantile}, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="${scope}"})' | null               | "/graph"            | [quantile: 0.99]    || 'histogram_quantile(0.99, prometheus_http_response_size_bytes_bucket{instance="localhost:9090",handler="/graph"})'
  }
}
