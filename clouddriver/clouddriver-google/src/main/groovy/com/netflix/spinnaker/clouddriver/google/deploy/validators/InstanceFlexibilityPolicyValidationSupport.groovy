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

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import org.slf4j.Logger
import org.slf4j.LoggerFactory

final class InstanceFlexibilityPolicyValidationSupport {
  private InstanceFlexibilityPolicyValidationSupport() {}
  private static final Logger log =
    LoggerFactory.getLogger(InstanceFlexibilityPolicyValidationSupport)

  static final String REQUIRES_REGIONAL_CODE = "requiresRegional"
  static final String INCOMPATIBLE_WITH_EVEN_SHAPE_CODE = "incompatibleWithEvenShape"
  static final String NULL_SELECTION_CODE = "nullSelection"
  static final String MISSING_RANK_CODE = "missingRank"
  static final String NEGATIVE_RANK_CODE = "negativeRank"
  static final String EMPTY_MACHINE_TYPES_CODE = "emptyMachineTypes"
  static final String INSTANCE_TYPE_NOT_IN_SELECTIONS_WARNING =
    "instanceType '{}' is not present in any instanceFlexibilityPolicy.instanceSelections.machineTypes. " +
      "MIG flexibility selections may never directly use the template machine type."

  static final String REQUIRES_REGIONAL_MESSAGE =
    "Instance flexibility policy is only supported for regional server groups."
  static final String INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE =
    "Instance flexibility policy cannot be used with EVEN target distribution shape."
  static final String NULL_SELECTION_MESSAGE =
    "Instance flexibility policy must not contain null selection entries."
  static final String MISSING_RANK_MESSAGE =
    "Each instance selection must specify rank when multiple selections are configured."
  static final String NEGATIVE_RANK_MESSAGE =
    "Each instance selection rank must be zero or greater."
  static final String EMPTY_MACHINE_TYPES_MESSAGE =
    "Each instance selection must specify at least one machine type."

  static List<ValidationIssue> validate(BasicGoogleDeployDescription description) {
    def selections = description.instanceFlexibilityPolicy?.instanceSelections
    if (!selections) {
      return []
    }

    List<ValidationIssue> issues = []

    if (!description.regional) {
      issues.add(new ValidationIssue(REQUIRES_REGIONAL_CODE, REQUIRES_REGIONAL_MESSAGE))
    }

    // Regional MIG defaults to EVEN when targetShape is not explicitly provided.
    // Users must explicitly set targetShape (e.g. BALANCED, ANY, ANY_SINGLE_ZONE)
    // when using a flexibility policy; null is treated as EVEN and rejected.
    // See: https://cloud.google.com/compute/docs/instance-groups/about-instance-flexibility
    def targetShape = description.distributionPolicy?.targetShape?.trim()
    if (!targetShape || targetShape.equalsIgnoreCase("EVEN")) {
      issues.add(
        new ValidationIssue(
          INCOMPATIBLE_WITH_EVEN_SHAPE_CODE, INCOMPATIBLE_WITH_EVEN_SHAPE_MESSAGE))
    }

    if (selections.containsValue(null)) {
      issues.add(new ValidationIssue(NULL_SELECTION_CODE, NULL_SELECTION_MESSAGE))
    }

    // Rank is optional when there is exactly one selection. With multiple selections,
    // rank is required so preference order is explicit.
    def nonNullSelections = selections.values().findAll { it != null }
    if (nonNullSelections.size() > 1 && nonNullSelections.any { it.rank == null }) {
      issues.add(new ValidationIssue(MISSING_RANK_CODE, MISSING_RANK_MESSAGE))
    }

    if (selections.values().any { it != null && it.rank != null && it.rank < 0 }) {
      issues.add(new ValidationIssue(NEGATIVE_RANK_CODE, NEGATIVE_RANK_MESSAGE))
    }

    if (selections.values().any { it != null && !it.machineTypes }) {
      issues.add(new ValidationIssue(EMPTY_MACHINE_TYPES_CODE, EMPTY_MACHINE_TYPES_MESSAGE))
    }

    warnIfInstanceTypeMissingFromSelections(description, selections)

    return issues
  }

  /**
   * Emits a log warning (not a validation error) when the description's instanceType is absent
   * from every flexibility policy selection's machineTypes list.  This is intentionally a warning
   * rather than a rejection because GCE allows the instance template machine type to differ from
   * the flexibility policy selections — the MIG will use the selection machine types for new VMs,
   * not the template's machine type.
   *
   * @see <a href="https://cloud.google.com/compute/docs/instance-groups/about-instance-flexibility">
   *     Instance flexibility policy (GCP docs)</a>
   */
  private static void warnIfInstanceTypeMissingFromSelections(
    BasicGoogleDeployDescription description,
    Map<String, ?> selections) {
    def normalizedInstanceType = normalizeMachineType(description.instanceType)
    if (!normalizedInstanceType) {
      return
    }

    boolean foundInSelections = selections.values().any { selection ->
      selection?.machineTypes?.any { machineType ->
        normalizedInstanceType.equalsIgnoreCase(normalizeMachineType(machineType))
      }
    }

    if (!foundInSelections) {
      log.warn(INSTANCE_TYPE_NOT_IN_SELECTIONS_WARNING, description.instanceType)
    }
  }

  private static String normalizeMachineType(String machineType) {
    if (!machineType) {
      return null
    }
    String trimmed = machineType.trim()
    if (!trimmed) {
      return null
    }
    int lastSlash = trimmed.lastIndexOf('/')
    return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed
  }

  static void rejectIssues(
    ValidationErrors errors,
    String fieldName,
    String codePrefix,
    List<ValidationIssue> issues) {
    issues.each { ValidationIssue issue ->
      errors.rejectValue(fieldName, "${codePrefix}.${issue.codeSuffix}", issue.message)
    }
  }

  static void throwFirstIssue(List<ValidationIssue> issues) {
    if (!issues.isEmpty()) {
      throw new IllegalArgumentException(issues[0].message)
    }
  }

  static class ValidationIssue {
    final String codeSuffix
    final String message

    ValidationIssue(String codeSuffix, String message) {
      this.codeSuffix = codeSuffix
      this.message = message
    }
  }
}
