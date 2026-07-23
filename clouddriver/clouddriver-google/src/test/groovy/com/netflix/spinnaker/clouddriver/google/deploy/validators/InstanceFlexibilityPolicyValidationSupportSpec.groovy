/*
 * Copyright 2026 Harness, Inc.
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

import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceFlexibilityPolicy
import spock.lang.Specification
import spock.lang.Unroll

class InstanceFlexibilityPolicyValidationSupportSpec extends Specification {
  void "rejects flexibility policy on a zonal server group"() {
    given:
      def description = validDescription()
      description.regional = false

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == [InstanceFlexibilityPolicyValidationSupport.REQUIRES_REGIONAL_CODE]
      issues*.message == [InstanceFlexibilityPolicyValidationSupport.REQUIRES_REGIONAL_MESSAGE]
  }

  @Unroll
  void "rejects flexibility policy with #scenario target shape"() {
    given:
      def description = validDescription()
      description.distributionPolicy.targetShape = targetShape

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == [expectedCode]
      issues*.message == [expectedMessage]

    where:
      scenario            | targetShape | expectedCode                                                              | expectedMessage
      "missing"           | null        | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_CODE | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE
      "blank"             | "   "       | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_CODE | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE
      "explicit EVEN"     | "EVEN"      | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_CODE | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE
      "normalized EVEN"   | " even "    | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_CODE | InstanceFlexibilityPolicyValidationSupport.INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE
      "unsupported value" | "ANY_SHAPE" | InstanceFlexibilityPolicyValidationSupport.INVALID_TARGET_SHAPE_CODE         | InstanceFlexibilityPolicyValidationSupport.INVALID_TARGET_SHAPE_MESSAGE
  }

  @Unroll
  void "accepts normalized flexibility target shape #targetShape"() {
    given:
      def description = validDescription()
      description.distributionPolicy.targetShape = targetShape

    expect:
      InstanceFlexibilityPolicyValidationSupport.validate(description).isEmpty()

    where:
      targetShape << ["BALANCED", " any ", " any_single_zone "]
  }

  void "rejects null selection entries"() {
    given:
      def description = validDescription()
      description.instanceFlexibilityPolicy.instanceSelections = ["preferred": null]

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == [InstanceFlexibilityPolicyValidationSupport.NULL_SELECTION_CODE]
      issues*.message == [InstanceFlexibilityPolicyValidationSupport.NULL_SELECTION_MESSAGE]
  }

  void "rejects negative selection rank"() {
    given:
      def description = validDescription()
      description.instanceFlexibilityPolicy.instanceSelections.preferred.rank = -1

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == [InstanceFlexibilityPolicyValidationSupport.NEGATIVE_RANK_CODE]
      issues*.message == [InstanceFlexibilityPolicyValidationSupport.NEGATIVE_RANK_MESSAGE]
  }

  @Unroll
  void "rejects invalid machineTypes list #machineTypes"() {
    given:
      def description = validDescription(machineTypes)

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == [InstanceFlexibilityPolicyValidationSupport.EMPTY_MACHINE_TYPES_CODE]
      issues*.message == [InstanceFlexibilityPolicyValidationSupport.EMPTY_MACHINE_TYPES_MESSAGE]

    where:
      machineTypes << [null, [], [null], [null, null], [""], ["   "], [" ", " "], ["e2-standard-2", " "]]
  }

  void "rejects duplicate machine types across selections"() {
    given:
      def description = validDescription()
      description.instanceFlexibilityPolicy.instanceSelections.fallback =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
          machineTypes: ["e2-standard-2"])

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == ["duplicateMachineType"]
      issues*.message == ["Machine types must be unique across instance selections."]
  }

  void "rejects duplicate machine types within one selection"() {
    given:
      def description = validDescription(["e2-standard-2", "e2-standard-2"])

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == ["duplicateMachineType"]
      issues*.message == ["Machine types must be unique across instance selections."]
  }

  void "rejects whitespace URL and case-equivalent duplicate machine types"() {
    given:
      def description = validDescription(
        [" https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a/machineTypes/E2-STANDARD-2 "])
      description.instanceFlexibilityPolicy.instanceSelections.fallback =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
          machineTypes: ["zones/us-central1-b/machineTypes/e2-standard-2"])

    when:
      def issues = InstanceFlexibilityPolicyValidationSupport.validate(description)

    then:
      issues*.codeSuffix == ["duplicateMachineType"]
      issues*.message == ["Machine types must be unique across instance selections."]
  }

  void "accepts distinct machine types across selections"() {
    given:
      def description = validDescription(["e2-standard-2", "n2-standard-2"])
      description.instanceFlexibilityPolicy.instanceSelections.fallback =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
          machineTypes: ["c2-standard-4"])

    expect:
      InstanceFlexibilityPolicyValidationSupport.validate(description).isEmpty()
  }

  void "accepts multiple rankless selections with non-blank machine types"() {
    given:
      def description = validDescription()
      description.instanceFlexibilityPolicy.instanceSelections.fallback =
        new GoogleInstanceFlexibilityPolicy.InstanceSelection(
          machineTypes: ["n2-standard-2"])

    expect:
      InstanceFlexibilityPolicyValidationSupport.validate(description).isEmpty()
  }

  private static BasicGoogleDeployDescription validDescription(
    List<String> machineTypes = ["e2-standard-2"]) {
    def selection = new GoogleInstanceFlexibilityPolicy.InstanceSelection()
    selection.setMachineTypes(machineTypes)
    def policy = new GoogleInstanceFlexibilityPolicy()
    policy.setInstanceSelections(["preferred": selection])
    return new BasicGoogleDeployDescription(
      regional: true,
      instanceType: "e2-standard-2",
      distributionPolicy: new GoogleDistributionPolicy(
        zones: ["us-central1-a"],
        targetShape: "BALANCED"),
      instanceFlexibilityPolicy: policy)
  }
}
