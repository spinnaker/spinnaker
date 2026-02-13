/*
 * Copyright 2014 Google, Inc.
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
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler
import spock.lang.Shared
import spock.lang.Specification

class CopyLastGoogleServerGroupDescriptionValidatorSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final ANCESTOR_SERVER_GROUP_NAME = "$APPLICATION-$STACK-v000"
  private static final TARGET_SIZE = 3
  private static final IMAGE = "debian-7-wheezy-v20141108"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  CopyLastGoogleServerGroupDescriptionValidator validator

  GoogleClusterProvider googleClusterProvider

  void setupSpec() {
    def googleDeployDefaults = new GoogleConfiguration.DeployDefaults()
    validator = new CopyLastGoogleServerGroupDescriptionValidator(googleDeployDefaults: googleDeployDefaults)
    def credentialsRepo = new MapBackedCredentialsRepository(GoogleNamedAccountCredentials.CREDENTIALS_TYPE,
      new NoopCredentialsLifecycleHandler<>())
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(credentials)
    validator.credentialsRepository = credentialsRepo
  }

  void setup() {
    googleClusterProvider = Mock(GoogleClusterProvider)
    validator.googleClusterProvider = googleClusterProvider
  }

  void "pass validation with minimal description inputs"() {
    setup:
      def description = new BasicGoogleDeployDescription(source: [region: REGION,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with full description inputs"() {
    setup:
      def description = new BasicGoogleDeployDescription(application: APPLICATION,
                                                         stack: STACK,
                                                         targetSize: TARGET_SIZE,
                                                         image: IMAGE,
                                                         instanceType: INSTANCE_TYPE,
                                                         zone: ZONE,
                                                         source: [region: REGION,
                                                                  serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
                                                         accountName: ACCOUNT_NAME)
        def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new BasicGoogleDeployDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
  }

  void "instance flexibility policy on zonal server group fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setRank(1)
      selection.setMachineTypes(["n2-standard-8"])
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": selection])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: false,
        zone: ZONE
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.requiresRegional",
                             "Instance flexibility policy is only supported for regional server groups.")
  }

  void "instance flexibility policy with EVEN target shape fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setRank(1)
      selection.setMachineTypes(["n2-standard-8"])
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": selection])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: true,
        region: REGION,
        distributionPolicy: new com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy(
          zones: ["us-central1-a"],
          targetShape: "EVEN"
        )
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.incompatibleWithEvenShape",
                             "Instance flexibility policy cannot be used with EVEN target distribution shape.")
  }

  void "instance flexibility policy with omitted target shape fails validation due to implicit EVEN default"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setRank(1)
      selection.setMachineTypes(["n2-standard-8"])
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": selection])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: true,
        region: REGION
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.incompatibleWithEvenShape",
                             "Instance flexibility policy cannot be used with EVEN target distribution shape.")
  }

  void "partnerMetadata is rejected under v1 API"() {
    setup:
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        partnerMetadata: ["key": "value"]
      ), errors)

    then:
      1 * errors.rejectValue("partnerMetadata",
                             "copyLastGoogleServerGroupDescription.partnerMetadata.notSupportedInV1",
                             "partnerMetadata is not supported under the stable v1 compute API and will not be propagated to GCE.")
  }

  void "flexibility policy with null selection entry fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": null])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: true,
        region: REGION
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.nullSelection",
                             "Instance flexibility policy must not contain null selection entries.")
  }

  void "flexibility policy with missing rank fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setMachineTypes(["n2-standard-8"])
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": selection])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: true,
        region: REGION
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.missingRank",
                             "Each instance selection must specify rank.")
  }

  void "flexibility policy with negative rank fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setRank(-1)
      selection.setMachineTypes(["n2-standard-8"])
      def flexPolicy = new com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy()
      flexPolicy.setInstanceSelections(["preferred": selection])

    when:
      validator.validate([], new BasicGoogleDeployDescription(
        accountName: ACCOUNT_NAME,
        instanceFlexibilityPolicy: flexPolicy,
        regional: true,
        region: REGION
      ), errors)

    then:
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.negativeRank",
                             "Each instance selection rank must be zero or greater.")
  }

  void "inherited flexibility with EVEN target shape fails validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def ancestorServerGroup = buildAncestorServerGroupWithFlexibilityPolicy("EVEN")
      def description = new BasicGoogleDeployDescription(
        source: [region: REGION, serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
        accountName: ACCOUNT_NAME
      )

    when:
      validator.validate([], description, errors)

    then:
      1 * googleClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, ANCESTOR_SERVER_GROUP_NAME) >> ancestorServerGroup
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.incompatibleWithEvenShape",
                             "Instance flexibility policy cannot be used with EVEN target distribution shape.")
  }

  void "inherited flexibility with omitted target shape fails validation due to implicit EVEN default"() {
    setup:
      def errors = Mock(ValidationErrors)
      def ancestorServerGroup = buildAncestorServerGroupWithFlexibilityPolicy(null)
      def description = new BasicGoogleDeployDescription(
        source: [region: REGION, serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
        accountName: ACCOUNT_NAME
      )

    when:
      validator.validate([], description, errors)

    then:
      1 * googleClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, ANCESTOR_SERVER_GROUP_NAME) >> ancestorServerGroup
      1 * errors.rejectValue("instanceFlexibilityPolicy",
                             "copyLastGoogleServerGroupDescription.instanceFlexibilityPolicy.incompatibleWithEvenShape",
                             "Instance flexibility policy cannot be used with EVEN target distribution shape.")
  }

  void "request distribution policy override takes precedence over inherited EVEN shape"() {
    setup:
      def errors = Mock(ValidationErrors)
      def ancestorServerGroup = buildAncestorServerGroupWithFlexibilityPolicy("EVEN")
      def description = new BasicGoogleDeployDescription(
        source: [region: REGION, serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
        accountName: ACCOUNT_NAME,
        regional: true,
        distributionPolicy: new GoogleDistributionPolicy(
          zones: ["us-central1-a"],
          targetShape: "BALANCED"
        )
      )

    when:
      validator.validate([], description, errors)

    then:
      1 * googleClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, ANCESTOR_SERVER_GROUP_NAME) >> ancestorServerGroup
      0 * errors._
  }

  void "instance type mismatch with flexibility policy does not fail copy validation"() {
    setup:
      def errors = Mock(ValidationErrors)
      def selection = new GoogleInstanceFlexibilityPolicy.InstanceSelection()
      selection.setRank(1)
      selection.setMachineTypes(["zones/us-central1-a/machineTypes/n2-standard-8"])
      def flexibilityPolicy = new GoogleInstanceFlexibilityPolicy()
      flexibilityPolicy.setInstanceSelections(["preferred": selection])
      def description = new BasicGoogleDeployDescription(
        source: [region: REGION, serverGroupName: ANCESTOR_SERVER_GROUP_NAME],
        accountName: ACCOUNT_NAME,
        regional: true,
        region: REGION,
        instanceType: "f1-micro",
        instanceFlexibilityPolicy: flexibilityPolicy,
        distributionPolicy: new GoogleDistributionPolicy(
          zones: ["us-central1-a"],
          targetShape: "BALANCED"
        )
      )

    when:
      validator.validate([], description, errors)

    then:
      1 * googleClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, ANCESTOR_SERVER_GROUP_NAME) >> null
      0 * errors.rejectValue("instanceFlexibilityPolicy", _, _)
  }

  private static GoogleServerGroup.View buildAncestorServerGroupWithFlexibilityPolicy(String targetShape) {
    def selection = new GoogleInstanceFlexibilityPolicy.InstanceSelection()
    selection.setRank(1)
    selection.setMachineTypes(["n2-standard-8"])
    def flexibilityPolicy = new GoogleInstanceFlexibilityPolicy()
    flexibilityPolicy.setInstanceSelections(["preferred": selection])
    return new GoogleServerGroup(
      name: ANCESTOR_SERVER_GROUP_NAME,
      regional: true,
      region: REGION,
      asg: [desiredCapacity: 2],
      distributionPolicy: targetShape != null
        ? new GoogleDistributionPolicy(zones: ["us-central1-a", "us-central1-b"], targetShape: targetShape)
        : null,
      instanceFlexibilityPolicy: flexibilityPolicy
    ).view
  }
}
