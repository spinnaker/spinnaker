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
    someTemplate: 'some-label=${scope}'
  ]

  @Shared
  PrometheusMetricsService prometheusMetricsService = PrometheusMetricsService.builder().scopeLabel("instance").build()

  @Unroll
  void "Can dry-run query generation"() {
    given:
    CanaryConfig canaryConfig =
      CanaryConfig.builder()
        .templates(templates)
        .build()
    PrometheusCanaryMetricSetQueryConfig queryConfig =
      PrometheusCanaryMetricSetQueryConfig.builder()
        .resourceType(resourceType)
        .metricName("some-metric-name")
        .labelBindings(labelBindings)
        .customFilterTemplate(customFilterTemplate)
        .customFilter(customFilter)
        .build()
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig.builder()
        .query(queryConfig)
        .build()
    PrometheusCanaryScope prometheusCanaryScope =
      new PrometheusCanaryScope()
        .setLocation("us-central1")
        .setScope(scope)

    when:
    String query = prometheusMetricsService.buildQuery(canaryConfig, canaryMetricConfig, prometheusCanaryScope)

    then:
    query == expectedQuery

    where:
    labelBindings | customFilterTemplate | customFilter                               | resourceType       | scope               || expectedQuery
    ["a=b"]       | "someTemplate"       | null                                       | "gce_instance"     | "some-app-canary"   || "avg(some-metric-name{some-label=some-app-canary,a=b})"
    ["a=b"]       | "someTemplate"       | "customFilter=something,andSomething=else" | "gce_instance"     | "some-app-canary"   || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                 | "customFilter=something,andSomething=else" | "gce_instance"     | "some-server-group" || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                 | "customFilter=something,andSomething=else" | "aws_ec2_instance" | "some-server-group" || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                 | "customFilter=something,andSomething=else" | "anything_else"    | "some-server-group" || "avg(some-metric-name{customFilter=something,andSomething=else,a=b})"
    ["a=b"]       | null                 | null                                       | "gce_instance"     | "some-server-group" || 'avg(some-metric-name{a=b,instance=~"some-server-group-.{4}",zone=~".+/zones/us-central1-.{1}"})'
    ["a=b"]       | null                 | ""                                         | "gce_instance"     | "some-server-group" || 'avg(some-metric-name{a=b,instance=~"some-server-group-.{4}\",zone=~".+/zones/us-central1-.{1}"})'
    ["a=b"]       | null                 | null                                       | "aws_ec2_instance" | "some-server-group" || 'avg(some-metric-name{a=b,asg_groupName="some-server-group",zone=~"us-central1.{1}"})'
    ["a=b"]       | null                 | ""                                         | "aws_ec2_instance" | "some-server-group" || 'avg(some-metric-name{a=b,asg_groupName="some-server-group",zone=~"us-central1.{1}"})'
  }
}
