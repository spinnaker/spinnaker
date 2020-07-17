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

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization.UtilizationTargetType
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.kork.artifacts.model.Artifact
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
    "europe-west1-b", "europe-west1-c", "europe-west1-d",
  ]
  private static final ASIA_ZONES = [
    "asia-east1-a", "asia-east1-b", "asia-east1-c",
  ]
  private static final ALL_ZONES = US_ZONES + EURO_ZONES + ASIA_ZONES
  private static final VCPU_MAX_BY_LOCATION = [
    'us-east1-b': [vCpuMax: 32],
    'us-east1-c': [vCpuMax: 32],
    'us-east1-d': [vCpuMax: 32],
    'us-central1-a': [vCpuMax: 16],
    'us-central1-b': [vCpuMax: 32],
    'us-central1-c': [vCpuMax: 32],
    'us-central1-f': [vCpuMax: 32],
    'us-west1-a': [vCpuMax: 32],
    'us-west1-b': [vCpuMax: 32],
    'europe-west1-b': [vCpuMax: 16],
    'europe-west1-c': [vCpuMax: 32],
    'europe-west1-d': [vCpuMax: 32],
    'asia-east1-a': [vCpuMax: 32],
    'asia-east1-b': [vCpuMax: 32],
    'asia-east1-c': [vCpuMax: 32],
    'us-east1': [vCpuMax: 32],
    'us-central1': [vCpuMax: 32],
    'us-west1': [vCpuMax: 32],
    'europe-west1': [vCpuMax: 16],
    'asia-east1': [vCpuMax: 32]
  ]
  private static final REGION_TO_ZONES = [
    'us-east1': ['us-east1-b', 'us-east1-c', 'us-east1-d'],
    'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-c', 'us-central1-f'],
    'us-west1': ['us-west1-a', 'us-west1-b'],
    'europe-west1': ['europe-west1-b', 'europe-west1-c', 'europe-west1-d'],
    'asia-east1': ['asia-east1-a', 'asia-east1-b', 'asia-east1-c'],
  ]

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  @Shared
  GoogleNamedAccountCredentials credentials

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    credentials =
      new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT_NAME)
        .credentials(new FakeGoogleCredentials())
        .locationToInstanceTypesMap(VCPU_MAX_BY_LOCATION)
        .regionToZonesMap(REGION_TO_ZONES)
        .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
  }

  void "generic non-empty ok"() {
    setup:
      def errors = Mock(ValidationErrors)
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
    def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateName("", label)
    then:
      1 * errors.rejectValue(label, "${DECORATOR}.${label}.empty")
      0 * errors._
  }

  void "validate simple account name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials(ACCOUNT_NAME, accountCredentialsProvider)
    then:
      0 * errors._
  }

  void "empty account name"() {
    setup:
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateCredentials("Unknown", accountCredentialsProvider)

    then:
      1 * errors.rejectValue("credentials", "${DECORATOR}.credentials.invalid")
      0 * errors._
  }

  void "valid server group name"() {
    setup:
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateServerGroupName("")
    then:
      1 * errors.rejectValue("serverGroupName", "${DECORATOR}.serverGroupName.empty")
      0 * errors._
  }

  void "valid region name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_REGIONS.each { validator.validateRegion(it, credentials) }
    then:
      0 * errors._
  }

  void "invalid region name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateRegion("", credentials)
    then:
      1 * errors.rejectValue("region", "${DECORATOR}.region.empty")
      0 * errors._

    when:
      validator.validateRegion("Unchecked", credentials)
    then:
      1 * errors.rejectValue("region", "${DECORATOR}.region.invalid")
      0 * errors._

    when:
      validator.validateRegion(" ", credentials)
    then:
      1 * errors.rejectValue("region", "${DECORATOR}.region.invalid")
      0 * errors._
  }

  void "valid zone name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      ALL_ZONES.each { validator.validateZone(it, credentials) }
    then:
      0 * errors._
  }

  void "invalid zone name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateZone("", credentials)
    then:
      1 * errors.rejectValue("zone", "${DECORATOR}.zone.empty")
      0 * errors._

    when:
      validator.validateZone("Unchecked", credentials)
    then:
      1 * errors.rejectValue("zone", "${DECORATOR}.zone.invalid")
      0 * errors._

    when:
      validator.validateZone(" ", credentials)
    then:
      1 * errors.rejectValue("zone", "${DECORATOR}.zone.invalid")
      0 * errors._
  }

  void "valid network name"() {
    setup:
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateNetwork("")
    then:
      1 * errors.rejectValue("network", "${DECORATOR}.network.empty")
      0 * errors._
  }

  void "valid image name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.STRING, "Unchecked", null)
    then:
      0 * errors._

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.STRING, " ", null)
    then:
      0 * errors._

    when:
      validator.validateImage(null, "Unchecked", null)
    then:
      0 * errors._
  }

  void "invalid image name"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.STRING, "", null)
    then:
      1 * errors.rejectValue("image", "${DECORATOR}.image.empty")
      0 * errors._

    when:
      validator.validateImage(null, "", null)
    then:
      1 * errors.rejectValue("image", "${DECORATOR}.image.empty")
      0 * errors._
  }

  void "valid image artifact"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def artifact = Artifact.ArtifactBuilder.newInstance().type("gce/image").build()

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.ARTIFACT, "", artifact)
    then:
      0 * errors._
  }

  void "missing image artifact"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.ARTIFACT, "", null)
    then:
      1 * errors.rejectValue("imageArtifact", "${DECORATOR}.imageArtifact.empty")
      0 * errors._
  }

  void "invalid image artifact type"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)
      def artifact = Artifact.ArtifactBuilder.newInstance().type("github/file").build()

    when:
      validator.validateImage(BaseGoogleInstanceDescription.ImageSource.ARTIFACT, "", artifact)
    then:
      1 * errors.rejectValue("imageArtifact.type", "${DECORATOR}.imageArtifact.type.invalid")
      0 * errors._
  }

  void "valid instance name"() {
    setup:
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceName("")
    then:
      1 * errors.rejectValue("instanceName", "${DECORATOR}.instanceName.empty")
      0 * errors._
  }

  void "valid instance type"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("Unchecked", ZONE, credentials)
    then:
      0 * errors._

    when:
      validator.validateInstanceType(" ", ZONE, credentials)
    then:
      0 * errors._
  }

  void "invalid instance type"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.empty")
      0 * errors._
  }

  void "valid custom instance type"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("custom-2-2048", ZONE, credentials)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("custom-24-29696", ZONE, credentials)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("custom-24-29696", REGION, credentials)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("e2-custom-24-29696", REGION, credentials)
    then:
      0 * errors._

    when:
      validator.validateInstanceType("n2-custom-32-122880", REGION, credentials)
    then:
      0 * errors._
  }

  void "invalid custom instance type"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateInstanceType("custom1-6912", ZONE, credentials)
      validator.validateInstanceType("customs-12-3456", ZONE, credentials)
      validator.validateInstanceType("custom--1234", ZONE, credentials)
      validator.validateInstanceType("custom-1-2345678", ZONE, credentials)
    then:
      4 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Custom instance string must match pattern /(.*)-?custom-(\\d{1,2})-(\\d{3,6})/.")

    when:
      validator.validateInstanceType("custom-1-8448", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Memory per vCPU must be less than 8GB.")

    when:
      validator.validateInstanceType("custom-2-768", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Memory per vCPU must be greater than 0.5GB.")

    when:
      validator.validateInstanceType("custom-1-1000", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Total memory must be a multiple of 256MB.")

    when:
      validator.validateInstanceType("custom-18-18432", "us-central1-q", credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "us-central1-q not found.")

    when:
      validator.validateInstanceType("custom-24-24576", "us-central1-a", credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "us-central1-a does not support more than 16 vCPUs.")

    when:
      validator.validateInstanceType("custom-0-1024", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "vCPU count must be greater than or equal to 1.")

    when:
      validator.validateInstanceType("custom-3-3072", ZONE, credentials)
    then:
      1 * errors.rejectValue("instanceType", "${DECORATOR}.instanceType.invalid", "Above 1, vCPU count must be even.")
  }

  void "valid name list"() {
    setup:
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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
      def errors = Mock(ValidationErrors)
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

  @Unroll
  void "valid autoHealer maxUnavailable with fixed=#fixed and percent=#percent"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateAutoHealingPolicy(new GoogleAutoHealingPolicy(
        healthCheck: "some-hc",
        maxUnavailable: [fixed: fixed, percent: percent]))

    then:
      0 * errors._

    where:
      fixed | percent
      null  | 0
      null  | 100
      0     | null
      50    | null
      50    | 100
      null  | 100.25
      50.25 | null
      50.25 | 100.25
  }

  void "invalid autoHealer maxUnavailable"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardGceAttributeValidator(DECORATOR, errors)

    when:
      validator.validateAutoHealingPolicy(new GoogleAutoHealingPolicy(
        healthCheck: "some-hc",
        maxUnavailable: []))

    then:
      1 * errors.rejectValue("autoHealingPolicy.maxUnavailable", "decorator.autoHealingPolicy.maxUnavailable.neitherFixedNorPercent")

    when:
      validator.validateAutoHealingPolicy(new GoogleAutoHealingPolicy(
        healthCheck: "some-hc",
        maxUnavailable: [fixed: -1]))

    then:
      1 * errors.rejectValue("autoHealingPolicy.maxUnavailable.fixed", "decorator.autoHealingPolicy.maxUnavailable.fixed.negative")

    when:
      validator.validateAutoHealingPolicy(new GoogleAutoHealingPolicy(
        healthCheck: "some-hc",
        maxUnavailable: [percent: -1]))

    then:
      1 * errors.rejectValue("autoHealingPolicy.maxUnavailable.percent",
        "decorator.autoHealingPolicy.maxUnavailable.percent.rangeViolation",
        "decorator.autoHealingPolicy.maxUnavailable.percent must be between 0 and 100, inclusive.")
  }
}
