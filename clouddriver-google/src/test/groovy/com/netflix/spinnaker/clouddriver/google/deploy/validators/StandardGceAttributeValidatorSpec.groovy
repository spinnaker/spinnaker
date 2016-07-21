/*
 * Copyright 2015 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization.UtilizationTargetType
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StandardGceAttributeValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final DECORATOR = "decorator"
  private static final ZONE = "us-central1-f"
  private static final REGION = "asia-east1"
  private static final ALL_REGIONS = [
    "us-central1", "europe-west1", "asia-east1",
  ]
  private static final US_ZONES = [
    "us-central1-a", "us-central1-b", "us-central1-c", "us-central1-f",
  ]
  private static final EURO_ZONES = [
    "europe-west1-a", "europe-west1-b", "europe-west1-c", "europe-west1-d",
  ]
  private static final ASIA_ZONES = [
    "asia-east1-a", "asia-east1-b", "asia-east1-c",
  ]
  private static final ALL_ZONES = US_ZONES + EURO_ZONES + ASIA_ZONES

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
  }

  void "generic non-empty ok"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNotEmpty("something", label)
    then:
      0 * errors._

    when:
      validator.validateNotEmpty(["something"], label)
    then:
      0 * errors._

    when:
      // Even though the list contains empty elements, the list itself is not empty.
      validator.validateNotEmpty([""], label)
    then:
      0 * errors._

    when:
      // Even though the list contains null elements, the list itself is not empty.
      validator.validateNotEmpty([null], label)
    then:
      0 * errors._
  }

  @Unroll
  void "expect non-empty ok with numeric values"() {
    setup:
    def errors = Mock(Errors)
    def validator = new StandardGceAttributeValidator(DECORATOR, errors)
    def label = "testAttribute"

    when:
    validator.validateNotEmpty(new Integer(intValue), label)
    then:
    0 * errors._

    where:
    intValue << [-1, 0, 1]
  }

  void "expect non-empty to fail with empty"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNotEmpty(null, label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._

    when:
      validator.validateNotEmpty([], label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._
  }

  void "nonNegativeInt ok if non-negative"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNonNegativeLong(0, label)
    then:
      0 * errors._

    when:
      validator.validateNonNegativeLong(1, label)
    then:
      0 * errors._

    when:
      validator.validateNonNegativeLong(1 << 30, label)  // unlimited
    then:
      0 * errors._
  }

  void "nonNegativeInt invalid if negative"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateNonNegativeLong(-1, label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.negative")
      0 * errors._
  }

  void "valid generic name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateName("Unchecked", "label")
    then:
      0 * errors._

    when:
      validator.validateName(" ", "label")
    then:
      0 * errors._
  }

  void "invalid generic name"() {
    setup:
      def label = "label"
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateName("", label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._
  }

  void "validate simple account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials(ACCOUNT_NAME, accountCredentialsProvider)
    then:
      0 * errors._
  }

  void "empty account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials(null, accountCredentialsProvider)
    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.empty")
      0 * errors._

    when:
      validator.validateCredentials("", accountCredentialsProvider)
    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.empty")
      0 * errors._
  }

  void "unknown account name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials("Unknown", accountCredentialsProvider)

    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.invalid")
      0 * errors._
  }

  void "valid server group name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateServerGroupName("Unchecked ")
    then:
      0 * errors._

    when:
      validator.validateServerGroupName(" ")
    then:
      0 * errors._
  }

  void "invalid server group name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateServerGroupName("")
    then:
      1 * errors.rejectValue("serverGroupName", "${DECORATOR}.serverGroupName.empty")
      0 * errors._
  }

  void "valid region name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_REGIONS.each { validator.validateRegion(it) }
    then:
      0 * errors._

    when:
      validator.validateRegion("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateRegion(" ")
    then:
      0 * errors._
  }

  void "invalid region name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateRegion("")
    then:
      1 * errors.rejectValue("region", "${DECORATOR}.region.empty")
      0 * errors._
  }

  void "valid zone name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_ZONES.each { validator.validateZone(it) }
    then:
      0 * errors._

    when:
      validator.validateZone("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateZone(" ")
    then:
      0 * errors._
  }

  void "invalid zone name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateZone("")
    then:
      1 * errors.rejectValue("zone", "${DECORATOR}.zone.empty")
      0 * errors._
  }

  void "valid network name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNetwork("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateNetwork(" ")
    then:
      0 * errors._
  }

  void "invalid network name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNetwork("")
    then:
      1 * errors.rejectValue("network", "${DECORATOR}.network.empty")
      0 * errors._
  }

  void "valid image name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateImage(" ")
    then:
      0 * errors._
  }

  void "invalid image name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage("")
    then:
      1 * errors.rejectValue("image", "${DECORATOR}.image.empty")
      0 * errors._
  }

  void "valid instance name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceName("Unchecked")
    then:
      0 * errors._

    when:
      validator.validateInstanceName(" ")
    then:
      0 * errors._
  }

  void "invalid instance name"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceName("")
    then:
      1 * errors.rejectValue("instanceName", "${DECORATOR}.instanceName.empty")
      0 * errors._
  }

  void "valid instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("Unchecked", ZONE)
    then:
      0 * errors._

    when:
      validator.validateInstanceType(" ", ZONE)
    then:
      0 * errors._
  }

  void "invalid instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.empty")
      0 * errors._
  }

  void "valid custom instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("custom-2-2048", ZONE)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("custom-24-29696", ZONE)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("custom-24-29696", REGION)
    then:
      0 * errors._
  }

  void "invalid custom instance type"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("custom1-6912", ZONE)
      validator.validateInstanceType("customs-12-3456", ZONE)
      validator.validateInstanceType("custom--1234", ZONE)
      validator.validateInstanceType("custom-1-2345678", ZONE)
    then:
      4 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Custom instance string must match pattern /custom-\\d{1,2}-\\d{4,6}/.")

    when:
      validator.validateInstanceType("custom-1-6912", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Memory per vCPU must be less than 6.5GB.")

    when:
      validator.validateInstanceType("custom-2-1024", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Memory per vCPU must be greater than 0.9GB.")

    when:
      validator.validateInstanceType("custom-1-1000", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Total memory must be a multiple of 256MB.")

    when:
      validator.validateInstanceType("custom-1-1024", "atlantis")
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "atlantis not found.")

    when:
      validator.validateInstanceType("custom-24-24576", "us-central1-a")
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "us-central1-a does not support more than 16 vCPUs.")

    when:
      validator.validateInstanceType("custom-0-1024", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "vCPU count must be greater than or equal to 1.")

    when:
      validator.validateInstanceType("custom-3-3072", ZONE)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Above 1, vCPU count must be even.")
  }

  void "valid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList(["Unchecked"], "loadBalancerName")
      then:
      0 * errors._

    when:
      validator.validateNameList([" "], "loadBalancerName")
    then:
      0 * errors._

    when:
      validator.validateNameList(["a", "b", "c"], "loadBalancerName")
    then:
      0 * errors._
  }

  void "invalid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList([], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerNames.empty")
      0 * errors._

    when:
      validator.validateNameList([""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName0.empty")
      0 * errors._
  }

  void "mixed valid/invalid name list"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNameList(["good", ""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName1.empty")
      0 * errors._

    when:
      validator.validateNameList(["good", "", "another", ""], "loadBalancerName")
    then:
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName1.empty")
      1 * errors.rejectValue("loadBalancerNames", "${DECORATOR}.loadBalancerName3.empty")
      0 * errors._
  }

  void "valid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds(["Unchecked"])
    then:
      0 * errors._

    when:
      validator.validateInstanceIds([" "])
    then:
      0 * errors._

    when:
      validator.validateInstanceIds(["a", "b", "c"])
    then:
      0 * errors._
  }

  void "invalid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds([])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceIds.empty")
      0 * errors._

    when:
      validator.validateInstanceIds([""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId0.empty")
      0 * errors._
  }

  void "mixed valid/invalid instance ids"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceIds(["good", ""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId1.empty")
      0 * errors._

    when:
      validator.validateInstanceIds(["good", "", "another", ""])
    then:
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId1.empty")
      1 * errors.rejectValue("instanceIds", "${DECORATOR}.instanceId3.empty")
      0 * errors._
  }

  void "valid in range exclusive"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateInRangeExclusive(0, -1, 1, label)
      validator.validateInRangeExclusive(10, 0, 20,  label)
      validator.validateInRangeExclusive(0.5, 0, 1, label)
    then:
      0 * errors._
  }

  void "invalid in range exclusive"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def label = "testAttribute"

    when:
      validator.validateInRangeExclusive(-1, 0, 1, label)
      validator.validateInRangeExclusive(0, 0, 1, label)
      validator.validateInRangeExclusive(1, 0, 1, label)
    then:
      3 * errors.rejectValue(label, "decorator.testAttribute must be between 0.0 and 1.0.")
  }

  void "valid basic scaling policy"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def scalingPolicy = new GoogleAutoscalingPolicy(
        minNumReplicas: 1,
        maxNumReplicas: 10,
        coolDownPeriodSec: 60,
        cpuUtilization: new GoogleAutoscalingPolicy.CpuUtilization(utilizationTarget: 0.9))

    when:
      validator.validateAutoscalingPolicy(scalingPolicy)

    then:
      0 * errors._
  }

  void "valid complex scaling policy"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

      def scalingPolicy = new GoogleAutoscalingPolicy(
        minNumReplicas: 1,
        maxNumReplicas: 10,
        coolDownPeriodSec: 60,
        cpuUtilization: new GoogleAutoscalingPolicy.CpuUtilization(utilizationTarget: 0.7),
        loadBalancingUtilization: new GoogleAutoscalingPolicy.LoadBalancingUtilization(utilizationTarget: 0.7),
        customMetricUtilizations: [ new GoogleAutoscalingPolicy.CustomMetricUtilization(metric: "myMetric",
          utilizationTarget: 0.9,
          utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE) ])

    when:
      validator.validateAutoscalingPolicy(scalingPolicy)

    then:
      0 * errors._
  }

  void "invalid autoscaler min, max or cooldown"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(minNumReplicas: -5))

    then:
      1 * errors.rejectValue("autoscalingPolicy.minNumReplicas",
        "decorator.autoscalingPolicy.minNumReplicas.negative")

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(maxNumReplicas: -5))

    then:
    1 * errors.rejectValue("autoscalingPolicy.maxNumReplicas",
      "decorator.autoscalingPolicy.maxNumReplicas.negative")

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(coolDownPeriodSec: -5))

    then:
      1 * errors.rejectValue("autoscalingPolicy.coolDownPeriodSec",
        "decorator.autoscalingPolicy.coolDownPeriodSec.negative")

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(minNumReplicas: 5, maxNumReplicas: 3))

    then:
      1 * errors.rejectValue("autoscalingPolicy.maxNumReplicas",
        "decorator.autoscalingPolicy.maxNumReplicas.lessThanMin",
        "decorator.autoscalingPolicy.maxNumReplicas must not be less than " +
          "decorator.autoscalingPolicy.minNumReplicas.")
  }

  void "invalid autoscaler loadBalancingUtilization or cpuUtilization"() {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(
        cpuUtilization: new GoogleAutoscalingPolicy.CpuUtilization(utilizationTarget: -1)))

    then:
      1 * errors.rejectValue("autoscalingPolicy.cpuUtilization.utilizationTarget",
        "decorator.autoscalingPolicy.cpuUtilization.utilizationTarget " +
        "must be between 0.0 and 1.0.")

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(
        loadBalancingUtilization: new GoogleAutoscalingPolicy.LoadBalancingUtilization(utilizationTarget: -1)))

    then:
      1 * errors.rejectValue("autoscalingPolicy.loadBalancingUtilization.utilizationTarget",
        "decorator.autoscalingPolicy.loadBalancingUtilization.utilizationTarget " +
          "must be between 0.0 and 1.0.")
  }

  void "invalid autoscaler customMetricUtilizations" () {
    setup:
      def errors = Mock(Errors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(
        customMetricUtilizations: [ new GoogleAutoscalingPolicy.CustomMetricUtilization(utilizationTarget: -1,
          metric: "myMetric", utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE) ]))

    then:
      1 * errors.rejectValue("decorator.autoscalingPolicy.customMetricUtilizations[0].utilizationTarget",
                             "decorator.autoscalingPolicy.customMetricUtilizations[0].utilizationTarget " +
                             "must be greater than zero.")

    when:
      validator.validateAutoscalingPolicy(new GoogleAutoscalingPolicy(
        customMetricUtilizations: [ new GoogleAutoscalingPolicy.CustomMetricUtilization(utilizationTarget: 0.5,
          utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE ) ]))

    then:
      1 * errors.rejectValue("autoscalingPolicy.customMetricUtilizations[0].metric",
        "decorator.autoscalingPolicy.customMetricUtilizations[0].metric.empty")

  }
}
