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
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.exceptions.PreconditionFailureException
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.converter.ConversionException
import retrofit.converter.JacksonConverter

@Component
class ClusterSizePreconditionTask implements CloudProviderAware, RetryableTask, PreconditionTask {
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
    Boolean onlyEnabledServerGroups = false

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
        throw new ConfigurationException("Invalid configuration " + errors.join(', '))
      }
    }
  }

  Cluster readCluster(ComparisonConfig config, String credentials, String cloudProvider) {
    def response
    try {
      response = oortService.getCluster(config.application, credentials, config.cluster, cloudProvider)
    } catch (SpinnakerHttpException spinnakerHttpException) {
      if (spinnakerHttpException.getResponseCode() == 404) {
        return [:]
      }
      throw spinnakerHttpException
    }
    /**
     * @see {@link com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration#oortDeployService(com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration.ClouddriverRetrofitBuilder)}
     * it uses {@link com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler} and the internal logic of constructing the conversion exception is by wrapping the {@link RetrofitError}.
     * In the same way the below {@link ConversionException} is wrapped into {@link com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException} for time being.
     * This logic will remain until the {@link OortService} configurations are migrated/modified to retrofit 2.x, the same is applicable to below {@link JacksonConverter} as well.
     * **/
    JacksonConverter converter = new JacksonConverter(objectMapper)
    try {
      return (Cluster) converter.fromBody(response.body, Cluster)
    } catch (ConversionException ce) {
      throw new SpinnakerConversionException(RetrofitError.conversionError(response.url, response, converter, Cluster, ce))
    }
  }

  @Override
  TaskResult execute(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    ComparisonConfig config = stage.mapTo("/context", ComparisonConfig)
    config.validate()

    Cluster cluster = readCluster(config, config.credentials, cloudProvider)
    Map<String, List<ServerGroup>> serverGroupsByRegion = (cluster.serverGroups  ?: []).groupBy { it.region }

    def failures = []
    for (String region : config.regions) {
      def serverGroups = serverGroupsByRegion[region] ?: []

      if (config.onlyEnabledServerGroups) {
        serverGroups = serverGroups.findAll { it.disabled == false }
      }

      int actual = serverGroups.size()
      boolean acceptable = config.getOp().evaluate(actual, config.expected)
      if (!acceptable) {
        failures << "expected $config.comparison $config.expected ${config.onlyEnabledServerGroups ? 'enabled ' : ''}server groups in $region but found $actual: ${serverGroups*.name}. Please clean up the cluster to only have the specific number of server groups, or opt out of this check in your deploy stage."
      }
    }

    if (failures) {
      throw new PreconditionFailureException("Precondition check failed: ${failures.join(', ')}")
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

    private Operator(String value, Closure<Boolean> closure) {
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
