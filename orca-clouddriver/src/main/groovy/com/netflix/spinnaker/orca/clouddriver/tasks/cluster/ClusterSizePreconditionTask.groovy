/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.converter.ConversionException
import retrofit.converter.JacksonConverter

@Component
class ClusterSizePreconditionTask extends AbstractCloudProviderAwareTask implements RetryableTask, PreconditionTask {
  public static final String PRECONDITION_TYPE = 'clusterSize'

  final String preconditionType = PRECONDITION_TYPE
  final long backoffPeriod = 5000
  final long timeout = 30000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Canonical
  static class ComparisonConfig {
    String cluster
    Moniker moniker
    String comparison = '=='
    int expected = 1
    String credentials
    Set<String> regions

    public String getApplication() {
      moniker?.app ?: Names.parseName(cluster)?.app
    }

    public Operator getOp() {
      Operator.fromString(comparison)
    }

    public void validate() {
      def errors = []
      if (!cluster) {
        errors << 'Missing cluster'
      }
      if (!application) {
        errors << 'Unable to determine application for cluster ' + cluster
      }
      if (!op) {
        errors << 'Unsupported comparison ' + comparison
      }
      if (expected < 0) {
        errors << 'Invalid value for expected ' + expected
      }
      if (!regions) {
        errors << 'Missing regions'
      }
      if (errors) {
        throw new IllegalStateException("Invalid configuration " + errors.join(','))
      }
    }
  }

  Map readCluster(ComparisonConfig config, String credentials, String cloudProvider) {
    def response
    try {
      response = oortService.getCluster(config.application, credentials, config.cluster, cloudProvider)
    } catch (RetrofitError re) {
      if (re.kind == RetrofitError.Kind.HTTP && re.response.status == 404) {
        return [:]
      }
      throw re
    }

    JacksonConverter converter = new JacksonConverter(objectMapper)
    try {
      return converter.fromBody(response.body, Map) as Map
    } catch (ConversionException ce) {
      throw RetrofitError.conversionError(response.url, response, converter, Map, ce)
    }
  }

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    ComparisonConfig config = stage.mapTo("/context", ComparisonConfig)
    config.validate()

    Map cluster = readCluster(config, config.credentials, cloudProvider)
    Map<String, List<Map>> serverGroupsByRegion = ((cluster.serverGroups as List<Map>) ?: []).groupBy { it.region }

    def failures = []
    for (String region : config.regions) {
      def serverGroups = serverGroupsByRegion[region] ?: []
      int actual = serverGroups.size()
      boolean acceptable = config.getOp().evaluate(actual, config.expected)
      if (!acceptable) {
        failures << "$region - expected $config.expected server groups but found $actual : ${serverGroups*.name}"
      }
    }

    if (failures) {
      throw new IllegalStateException("Precondition failed: ${failures.join(',')}")
    }

    return TaskResult.SUCCEEDED
  }

  static enum Operator {
    LT('<', { a, e -> a < e}),
    LE('<=', { a, e -> a <= e}),
    EQ('==', { a, e -> a == e}),
    GE('>=', { a, e -> a >= e}),
    GT('>', { a, e -> a > e})

    private final String value
    private final Closure<Boolean> closure

    public Operator(String value, Closure<Boolean> closure) {
      this.value = value
      this.closure = closure
    }

    public static Operator fromString(String opString) {
      values().find { it.value == opString }
    }

    public boolean evaluate(int actualValue, int expectedValue) {
      closure.call(actualValue, expectedValue)
    }
  }
}
