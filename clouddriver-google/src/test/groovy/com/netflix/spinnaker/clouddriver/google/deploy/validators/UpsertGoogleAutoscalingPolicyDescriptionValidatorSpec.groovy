/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization.UtilizationTargetType
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleAutoscalingPolicyDescriptionValidatorSpec extends Specification {
  private static final SERVER_GROUP_NAME = "server-group-name"
  private static final ACCOUNT_NAME = "auto"
  private static final REGION = "us-central1"
  private static final CPU_UTILIZATION = new GoogleAutoscalingPolicy.CpuUtilization()
  private static final LOAD_BALANCING_UTILIZATION = new GoogleAutoscalingPolicy.LoadBalancingUtilization()
  private static final METRIC = "myMetric"
  private static final UTILIZATION_TARGET = 0.6
  private static final CUSTOM_METRIC_UTILIZATIONS = [new GoogleAutoscalingPolicy.CustomMetricUtilization(
    metric: METRIC,
    utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE,
    utilizationTarget: UTILIZATION_TARGET)]
  private static final MIN_NUM_REPLICAS = 1
  private static final MAX_NUM_REPLICAS = 10
  private static final COOL_DOWN_PERIOD_SEC = 60
  private static final GOOGLE_SCALING_POLICY = new GoogleAutoscalingPolicy(
    minNumReplicas: MIN_NUM_REPLICAS,
    maxNumReplicas: MAX_NUM_REPLICAS,
    coolDownPeriodSec: COOL_DOWN_PERIOD_SEC,
    cpuUtilization: CPU_UTILIZATION,
    loadBalancingUtilization: LOAD_BALANCING_UTILIZATION,
    customMetricUtilizations: CUSTOM_METRIC_UTILIZATIONS)

  @Shared
  UpsertGoogleAutoscalingPolicyDescriptionValidator validator

  void setupSpec() {
    validator = new UpsertGoogleAutoscalingPolicyDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: GOOGLE_SCALING_POLICY,
      accountName: ACCOUNT_NAME)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors._
  }

  void "null input fails validation"() {
    setup:
    def description = new UpsertGoogleAutoscalingPolicyDescription()
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('credentials', _)
    1 * errors.rejectValue('region', _)
    1 * errors.rejectValue('serverGroupName', _)
    1 * errors.rejectValue('autoscalingPolicy', _)
  }
}
