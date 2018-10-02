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

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactUtils
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleDiskType
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceTypeDisk
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.springframework.validation.Errors

/**
 * Common validation routines for standard description attributes.
 *
 * This is a helper class intended to be instantiated once per data object being validated
 * and used to validate several attributes. This validator instance keeps the accumulated
 * errors and {@code context} decorator used for encountered error strings.
 *
 * The individual type validation methods return {@code true} on success and {@code false} on error.
 * Error descriptions are added into the {@code errors} object bound at construction. When errors
 * are encountered, they are normally rejected with a message in the form {@code context.attribute.policy}
 * where:
 * <ul>
 *   <li> {@code context} is the descriptor owning the attribute.
 *   <li> {@code attribute} is the name of the particular attribute whose validation failed.
 *        In the event of repeated attributes (e.g. a list) then this name
 *        is decorated with the index of the specific instance.
 *   <li> {@code policy} indicates the policy that was violated causing the error.
 * </ul>
 *
 * Standard policies are currently:
 * <ul>
 *   <li> {@code "empty"} indicates an empty (or null) string or list value.
 *   <li> {@code "invalid"} indicates some other error.
 *   <li> {@code "negative"} integer was negative.
 * </ul>
 *
 * Note that when a validation is rejected, {@code Errors.rejectValue} is called with the name of the
 * attribute that failed, and the policy message described above.
 *
 * Examples:
 * <ul>
 *   <li> {@code rejectValue "instanceIds", "ResetGoogleInstancesDescriptor.instanceIds.empty"}<br/>
 *        The {@code instanceIds} attribute of the {@ResetGoogleInstancesDescriptor} object had no elements
 *        or {@code null}.
 *   <li> {@code rejectValue "instanceIds", "ResetGoogleInstancesDescriptor.instanceId0.empty"}<br/>
 *        The first ({@code 0}-indexed) {@code instanceId} of the {@code instanceIds} attribute of
 *        the {@ResetGoogleInstancesDescriptor} object was empty or {@code null}.
 * </ul>
 */
class StandardGceAttributeValidator {
  /**
   * Bound at construction, contains the name of the type being validated used to decorate errors.
   */
  String context

  /**
   * Bound at construction, this is used to collect validation errors.
   */
  Errors errors

  /**
   * Constructs validator for standard attributes added by GCE.
   *
   * @param context The owner of the attributes to be validated is typically a {@code *Description} class.
   * @param errors  Accumulates and reports on the validation errors over the lifetime of this validator.
   */
  StandardGceAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  /**
   * Validate a value that should not be empty.
   *
   * @param value The value cannot be null, empty string, or an empty container.
   *              However it can be a container of only empty values.
   * @param attribute The attribute and part names are the same, meaning this is
   *              suitable for 'primitive' values or composite types treated as a whole.
   *
   * @see validateNotEmptyAsPart
   */
  def validateNotEmpty(Object value, String attribute) {
    validateNotEmptyAsPart(value, attribute, attribute)
  }

  /**
   * Validate a value that should not be empty.
   *
   * @param value The value cannot be null, empty string, or an empty container.
   *              However it can be a container of only empty values. Any
   *              numeric value is considered non-empty.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNotEmptyAsPart(Object value, String attribute, String part) {
    if (value || value instanceof Number) {
      return true
    } else {
      errors.rejectValue(attribute, "${context}.${part}.empty")
      return false
    }
  }

  /**
   * Validates {@code value} as a valid generic GCE name.
   *
   * @param value The name being validated.
   *              However it can be a container of only empty values.
   * @param attribute The attribute and part names are the same, meaning this is
   *              suitable for 'primitive' values or composite types treated as a whole.
   *
   * @see validateNameAsPart
   */
  def validateName(String value, String attribute) {
    validateNameAsPart(value, attribute, attribute)
  }

  /**
   * Validates {@code value} as a valid generic GCE name.
   *
   * Specific resource types may have more specific validation rules.
   * This validator is just treating {@code value} as a generic GCE name,
   * which cannot be empty and may have additional constraints (symbols, etc).
   *
   * @param value The name being validated.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNameAsPart(String value, String attribute, String part) {
    def result = validateNotEmptyAsPart(value, attribute, part)

    // TODO(ewiseblatt): 20150323
    // Add additional rules here, if there are standard GCE naming rules.
    //
    // (e.g. see https://cloud.google.com/compute/docs/load-balancing/network/forwarding-rules#forwarding_rule_properties)
    // says the name must be 1-63 chars long and match the RE [a-z]([-a-z0-9]*[a-z0-9])?
    // However, I dont notice these explicit constraints documented for all naming context.
    return result
  }

  def validateCredentials(String accountName, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(accountName, "credentials")
    if (result) {
      def credentials = accountCredentialsProvider.getCredentials(accountName)

      if (!(credentials?.credentials instanceof GoogleCredentials)) {
        errors.rejectValue("credentials", "${context}.credentials.invalid")
        result = false
      }
    }
    return result
  }

  def validateRegion(String region, GoogleNamedAccountCredentials credentials) {
    if (validateNotEmpty(region, "region") && credentials) {
      if (!(region in credentials.regionToZonesMap)) {
        errors.rejectValue("region", "${context}.region.invalid")
      } else if (!(region in credentials.regions*.name)) {
        errors.rejectValue("region", "${context}.region.unconfigured")
      }
    }
  }

  def validateZone(String zone, GoogleNamedAccountCredentials credentials) {
    if (validateNotEmpty(zone, "zone") && credentials) {
      if (!credentials.regionFromZone(zone)) {
        errors.rejectValue("zone", "${context}.zone.invalid")
      } else if (!(credentials.regionFromZone(zone) in credentials.regions*.name)) {
        errors.rejectValue("zone", "${context}.zone.unconfigured")
      }
    }
  }

  def validateNetwork(String network) {
    validateNotEmpty(network, "network")
  }

  def validateNonNegativeLong(long value, String attribute) {
    def result = true
    if (value < 0) {
      errors.rejectValue attribute, "${context}.${attribute}.negative"
      result = false
    }
    return result
  }

  def validateInRangeExclusive(double value, double min, double max, String attribute) {
    def result = true
    if (value <= min || value >= max) {
      errors.rejectValue attribute, "${context}.${attribute} must be between ${min} and ${max}."
      result = false
    }
    return result
  }

  def validateInRangeInclusive(int value, int min, int max, String attribute) {
    def result = true
    if (value < min || value > max) {
      errors.rejectValue(attribute,
                         "${context}.${attribute}.rangeViolation",
                         "${context}.${attribute} must be between ${min} and ${max}, inclusive.")
      result = false
    }
    return result
  }

  def validateMaxNotLessThanMin(long minValue, long maxValue, String minAttribute, String maxAttribute) {
    def result = true
    if (maxValue < minValue) {
      errors.rejectValue(maxAttribute,
                         "${context}.${maxAttribute}.lessThanMin",
                         "${context}.${maxAttribute} must not be less than ${context}.${minAttribute}.")
      result = false
    }
    return result
  }

  def validateServerGroupName(String serverGroupName) {
    validateName(serverGroupName, "serverGroupName")
  }

  def validateImage(BaseGoogleInstanceDescription.ImageSource imageSource, String image, Artifact imageArtifact) {
    if (imageSource == BaseGoogleInstanceDescription.ImageSource.ARTIFACT) {
      validateImageArtifact(imageArtifact)
    } else {
      validateNotEmpty(image, "image")
    }
  }

  def validateImageArtifact(Artifact imageArtifact) {
    if (!validateNotEmpty(imageArtifact, "imageArtifact")) {
      // If there's no artifact at all, return early rather than try to validate the null artifact
      return false
    }
    if (imageArtifact.getType() != ArtifactUtils.GCE_IMAGE_TYPE) {
      errors.rejectValue("imageArtifact.type", "${context}.imageArtifact.type.invalid")
    }
  }

  def validateImageName(String imageName) {
    validateNotEmpty(imageName, "imageName")
  }

  def validateNameList(List<String> names, String componentDescription) {
    validateNameListHelper(names, componentDescription, false)
  }

  def validateOptionalNameList(List<String> names, String componentDescription) {
    validateNameListHelper(names, componentDescription, true)
  }

  def validateNameListHelper(List<String> names, String componentDescription, boolean emptyListIsOk) {
    if (emptyListIsOk && !names) {
      return true
    }

    def result = validateNotEmpty(names, "${componentDescription}s")
    names.eachWithIndex { value, index ->
      result &= validateNameAsPart(value, "${componentDescription}s", "$componentDescription$index")
    }
    return result
  }

  def validateInstanceIds(List<String> instanceIds) {
    return validateNameList(instanceIds, "instanceId")
  }

  def validateInstanceName(String instanceName) {
    validateName(instanceName, "instanceName")
  }

  def validateInstanceType(String instanceType, String location, GoogleNamedAccountCredentials credentials) {
    validateNotEmpty(instanceType, "instanceType")
    if (instanceType?.startsWith('custom')) {
      validateCustomInstanceType(instanceType, location, credentials)
    }
  }

  def customInstanceRegExp = /custom-\d{1,2}-\d{4,6}/

  def validateCustomInstanceType(String instanceType, String location, GoogleNamedAccountCredentials credentials) {
    if (!(instanceType ==~ customInstanceRegExp)) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "Custom instance string must match pattern /custom-\\d{1,2}-\\d{4,6}/.")
      return false
    }

    def ( vCpuCount, memory ) = instanceType.split('-').tail().collect { it.toDouble() }
    def memoryInGbs = memory / 1024

    // Memory per vCPU must be between .9 GB and 6.5 GB
    def maxMemory = vCpuCount * 6.5
    def minMemory = Math.ceil((0.9 * vCpuCount) * 4) / 4

    if (vCpuCount < 1) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "vCPU count must be greater than or equal to 1.")
      return false
    }

    if (vCpuCount != 1 && vCpuCount % 2 == 1) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "Above 1, vCPU count must be even.")
    }

    if (memoryInGbs > maxMemory) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "Memory per vCPU must be less than 6.5GB.")
    }

    if (memoryInGbs < minMemory) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "Memory per vCPU must be greater than 0.9GB.")
    }

    if (memory % 256 != 0) {
      errors.rejectValue("instanceType", "${context}.instanceType.invalid", "Total memory must be a multiple of 256MB.")
    }

    if (location) {
      if (location in credentials.locationToInstanceTypesMap) {
        def vCpuMaxForLocation = credentials.locationToInstanceTypesMap[location]?.vCpuMax

        if (vCpuCount > vCpuMaxForLocation) {
          errors.rejectValue("instanceType", "${context}.instanceType.invalid", "${location} does not support more than ${vCpuMaxForLocation} vCPUs.")
        }
      } else {
        errors.rejectValue("instanceType", "${context}.instanceType.invalid", "${location} not found.")
      }
    }
  }

  def validateMinCpuPlatform(String minCpuPlatform, String location, GoogleNamedAccountCredentials credentials) {
    if (!credentials.locationToCpuPlatformsMap[location]?.contains(minCpuPlatform)) {
      errors.rejectValue("minCpuPlatform", "${context}.minCpuPlatform.invalid", "CPU platform $minCpuPlatform is not available in $location.")
    }
  }

  def validateInstanceTypeDisks(GoogleInstanceTypeDisk instanceTypeDisk, List<GoogleDisk> specifiedDisks) {
    // The fields type and sizeGb are required on each disk, and sizeGb must be greater than zero.
    specifiedDisks.eachWithIndex { disk, index ->
      validateNotEmptyAsPart(disk.type, "disks", "disk${index}.type")
      validateNotEmptyAsPart(disk.sizeGb, "disks", "disk${index}.sizeGb")
      validateNonNegativeLong(disk.sizeGb ?: 0, "disk${index}.sizeGb")
    }

    if (specifiedDisks) {
      // Must specify exactly one persistent disk.
      int persistentDiskCount = specifiedDisks.findAll { it.persistent }.size()

      if (!persistentDiskCount) {
        errors.rejectValue("disks",
                           "${context}.disks.missingPersistentDisk",
                           "A persistent boot disk is required.")
      }
    }

    // Persistent disks must be at least 10GB.
    specifiedDisks.findAll { it.persistent }.eachWithIndex { persistentDisk, index ->
      if (persistentDisk.sizeGb < 10) {
        errors.rejectValue("disks",
                           "${context}.disk${index}.sizeGb.invalidSize",
                           "Persistent disks must be at least 10GB.")
      }
    }

    // sourceImage must not be specified on the first persistent disk, and is required on all remaining persistent disks.
    def firstPersistentDisk = specifiedDisks.find { it.persistent }

    specifiedDisks.eachWithIndex { disk, index ->
      if (disk.is(firstPersistentDisk)) {
        if (firstPersistentDisk.sourceImage) {
          errors.rejectValue("disks",
                             "${context}.disk${index}.sourceImage.unexpected",
                             "The boot disk must not specify source image, it must be specified at the top-level on the request as `image`.")
        }
      } else if (disk.persistent && !disk.sourceImage) {
        errors.rejectValue("disks",
                           "${context}.disk${index}.sourceImage.required",
                           "All non-boot persistent disks are required to specify source image.")
      }
    }

    specifiedDisks.findAll { it.type == GoogleDiskType.LOCAL_SSD }.eachWithIndex { localSSDDisk, index ->
      // Shared-core instance types do not support local-ssd.
      if (!instanceTypeDisk.supportsLocalSSD) {
        errors.rejectValue("disks",
                           "${context}.disk${index}.type.localSSDUnsupported",
                           "Instance type $instanceTypeDisk.instanceType does not support Local SSD.")
      }

      // local-ssd disks must be exactly 375GB.
      if (localSSDDisk.sizeGb != 375) {
        errors.rejectValue("disks",
                           "${context}.disk${index}.sizeGb.invalidSize",
                           "Local SSD disks must be exactly 375GB.")
      }

      // local-ssd disks must have auto-delete set.
      if (!localSSDDisk.autoDelete) {
        errors.rejectValue("disks",
                           "${context}.disk${index}.autoDelete.required",
                           "Local SSD disks must have auto-delete set.")
      }
    }
  }

  def validateAutoscalingPolicy(GoogleAutoscalingPolicy policy) {
    if (policy) {
      policy.with {
        if (minNumReplicas != null) {
          validateNonNegativeLong(minNumReplicas, "autoscalingPolicy.minNumReplicas")
        }

        if (maxNumReplicas != null) {
          validateNonNegativeLong(maxNumReplicas, "autoscalingPolicy.maxNumReplicas")
        }

        if (coolDownPeriodSec != null) {
          validateNonNegativeLong(coolDownPeriodSec, "autoscalingPolicy.coolDownPeriodSec")
        }

        if (minNumReplicas != null && maxNumReplicas != null) {
          validateMaxNotLessThanMin(minNumReplicas,
                                    maxNumReplicas,
                                    "autoscalingPolicy.minNumReplicas",
                                    "autoscalingPolicy.maxNumReplicas")
        }

        customMetricUtilizations.eachWithIndex { utilization, index ->
          def path = "autoscalingPolicy.customMetricUtilizations[${index}]"

          utilization.with {
            validateNotEmpty(metric, "${path}.metric")

            if (utilizationTarget <= 0) {
              errors.rejectValue("${context}.${path}.utilizationTarget",
                                 "${context}.${path}.utilizationTarget must be greater than zero.")
            }

            validateNotEmpty(utilizationTargetType, "${path}.utilizationTargetType")
          }
        }
      }

      [ "cpuUtilization", "loadBalancingUtilization" ].each {
        if (policy[it] != null && policy[it].utilizationTarget != null) {
          validateInRangeExclusive(policy[it].utilizationTarget,
                                   0, 1, "autoscalingPolicy.${it}.utilizationTarget")
        }
      }
    }
  }

  def validateAutoHealingPolicy(GoogleAutoHealingPolicy policy, boolean rejectEmptyMaxUnavailable = true) {
    policy?.with {
      if (initialDelaySec != null) {
        validateNonNegativeLong(initialDelaySec, "autoHealingPolicy.initialDelaySec")
      }

      if (healthCheck != null) {
        validateName(healthCheck, "autoHealingPolicy.healthCheck")

        maxUnavailable?.with {
          if (fixed != null) {
            validateNonNegativeLong(fixed as int, "autoHealingPolicy.maxUnavailable.fixed")
          } else if (percent != null) {
            validateInRangeInclusive(percent as int,
                                     0, 100, "autoHealingPolicy.maxUnavailable.percent")
          } else if (rejectEmptyMaxUnavailable) {
            this.errors.rejectValue("autoHealingPolicy.maxUnavailable",
                                    "${this.context}.autoHealingPolicy.maxUnavailable.neitherFixedNorPercent")
          }
        }
      }
    }
  }

  def validateTags(List<String> tags) {
    return validateOptionalNameList(tags, "tag")
  }

  def validateMap(Map<String, String> mappings, String componentDescription) {
    if (mappings == null) {
      errors.rejectValue("${componentDescription}s", "${context}.${componentDescription}s.empty")
      return false
    }

    def result = true

    mappings.each { key, value ->
      result &= validateNameAsPart(key, "${componentDescription}s", "${componentDescription}s.key")

      // Keys can be mapped to empty values.
      if (value) {
        result &= validateNameAsPart(value, "${componentDescription}s", "$componentDescription.${key}.value")
      }
    }

    return result
  }

  def validateAuthScopes(List<String> authScopes) {
    return validateOptionalNameList(authScopes, "authScope")
  }
}
