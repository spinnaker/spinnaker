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


package com.netflix.spinnaker.kato.deploy.aws.validators

import com.netflix.spinnaker.kato.config.KatoAWSConfig.AwsConfigurationProperties
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

public abstract class AmazonDescriptionValidationSupport<T> extends DescriptionValidator<T> {

  abstract void validate(List priorDescriptions, T description, Errors errors)

  @Autowired
  AwsConfigurationProperties awsConfigurationProperties

  void validateAsgNameAndRegions(T description, Errors errors) {
    validateAsgName description, errors
    validateRegions description, errors

  }

  static void validateAsgName(T description, Errors errors) {
    def key = description.getClass().simpleName
    if (!description.asgName) {
      errors.rejectValue("asgName", "${key}.asgName.empty")
    }
  }

  void validateRegions(T description, Errors errors) {
    def key = description.getClass().simpleName
    validateRegions(description.regions, key, errors)
  }

  void validateRegion(String regionName, String errorKey, Errors errors) {
    validateRegions([regionName], errorKey, errors, "region")
  }

  void validateRegions(Collection<String> regionNames, String errorKey, Errors errors, String attributeName = "regions") {
    if (!regionNames) {
      errors.rejectValue(attributeName, "${errorKey}.${attributeName}.empty")
    } else if (awsConfigurationProperties.regions && !awsConfigurationProperties.regions.containsAll(regionNames)) {
      errors.rejectValue(attributeName, "${errorKey}.${attributeName}.not.configured")
    }
  }

  static void validateCapacity(T description, Errors errors) {
    if (description.capacity.min > description.capacity.max) {
      errors.rejectValue "capacity", "resizeAsgDescription.capacity.transposed", [description.capacity.min, description.capacity.max] as String[], "Capacity min and max appear transposed"
    }
    if (description.capacity.desired < description.capacity.min || description.capacity.desired > description.capacity.max) {
      errors.rejectValue "capacity", "resizeAsgDescription.desired.capacity.not.in.range", [description.capacity.min, description.capacity.max, description.capacity.desired] as String[], "Desired capacity is not within min/max range"
    }
  }
}
