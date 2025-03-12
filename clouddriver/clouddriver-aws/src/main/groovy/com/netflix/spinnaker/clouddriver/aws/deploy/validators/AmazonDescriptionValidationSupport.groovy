/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors

public  abstract class AmazonDescriptionValidationSupport<T extends AbstractAmazonCredentialsDescription> extends DescriptionValidator<T> {

  abstract void validate(List priorDescriptions, T description, ValidationErrors errors)

  void validateAsgs(T description, ValidationErrors errors) {
    if (!description.asgs) {
      errors.rejectValue("asgs", "${description.getClass().simpleName}.empty")
    } else {
      description.asgs.each { AsgDescription asgDescription ->
        validateAsgDescription description, asgDescription, errors
      }
    }
  }

  void validateAsgDescription(T description, AsgDescription asgDescription, ValidationErrors errors) {
    def key = description.getClass().simpleName
    if (!asgDescription.serverGroupName) {
      errors.rejectValue("serverGroupName", "${key}.serverGroupName.empty")
    }

    if (!asgDescription.region) {
      errors.rejectValue("region", "${key}.region.empty")
    } else {
      validateRegions description, [asgDescription.region], key, errors
    }
  }

  void validateAsgsWithCapacity(T description, ValidationErrors errors) {
    if (!description.asgs) {
      errors.rejectValue("asgs", "${description.getClass().simpleName}.empty")
    } else {
      description.asgs.each { ResizeAsgDescription.AsgTargetDescription asgDescription ->
        validateAsgDescriptionWithCapacity description, asgDescription, errors
      }
    }
  }

  void validateAsgDescriptionWithCapacity(T description, ResizeAsgDescription.AsgTargetDescription asgDescription, ValidationErrors errors) {
    validateAsgDescription description, asgDescription, errors
    validateCapacity asgDescription, errors
  }

  void validateAsgName(T description, ValidationErrors errors) {
    def key = description.getClass().simpleName
    if (!description.asgName) {
      errors.rejectValue("asgName", "${key}.asgName.empty")
    }
  }

  void validateRegion(T description, String regionName, String errorKey, ValidationErrors errors) {
    validateRegions(description, regionName ? [regionName] : [], errorKey, errors, "region")
  }

  void validateRegions(T description, Collection<String> regionNames, String errorKey, ValidationErrors errors, String attributeName = "regions") {
    if (!regionNames) {
      errors.rejectValue(attributeName, "${errorKey}.${attributeName}.empty")
    } else {
      def allowedRegions = description.credentials?.regions?.name
      if (allowedRegions && !allowedRegions.containsAll(regionNames)) {
        errors.rejectValue(attributeName, "${errorKey}.${attributeName}.not.configured")
      }
    }
  }

  void validateAsgNameAndRegionAndInstanceIds(T description, ValidationErrors errors) {
    def key = description.class.simpleName
    if (description.asgName) {
      validateAsgName(description, errors)
    }

    validateRegion(description, description.region, key, errors)
    if (!description.instanceIds) {
      errors.rejectValue("instanceIds", "${key}.instanceIds.empty")
    } else {
      description.instanceIds.each {
        if (!it) {
          errors.rejectValue("instanceIds", "${key}.instanceId.invalid")
        }
      }
    }
  }

  static void validateCapacity(def description, ValidationErrors errors) {
    Integer min = description.capacity.min
    Integer max = description.capacity.max
    Integer desired = description.capacity.desired
    if (min != null && max != null && min > max) {
      errors.rejectValue "capacity", "resizeAsgDescription.capacity.transposed",
        [min, max] as String[],
        "min size (${min}) > max size (${max})"
    }
    if (desired != null) {
      if ((min != null && desired < min) || (max != null && desired > max)) {
        errors.rejectValue "capacity", "resizeAsgDescription.desired.capacity.not.in.range",
          [min, max, desired] as String[],
          "desired capacity (${desired}) is not within min/max (${min}/${max}) range"
      }
    }
  }
}
