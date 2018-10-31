/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.deploy.validator

import org.springframework.validation.Errors
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.VersionedCloudProviderOperation

abstract class StandardOracleAttributeValidator<T> extends DescriptionValidator<T> {

  protected String context

  def validateNotEmptyString(Errors errors, String value, String attribute) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${attribute}.empty")
      return false
    }
    return true
  }

  def validateNonNegative(Errors errors, int value, String attribute) {
    def result
    if (value >= 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.negative")
      result = false
    }
    result
  }

  def validatePositive(Errors errors, int value, String attribute) {
    def result
    if (value > 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notPositive")
      result = false
    }
    result
  }
  
  def validateCapacity(Errors errors, Integer min, Integer max, Integer desired) {
    if (min != null && max != null && min > max) {
      errors.rejectValue "capacity", "${context}.capacity.transposed",
        [min, max] as String[],
        "min size (${min}) > max size (${max})"
    }
    if (desired != null) {
      if ((min != null && desired < min) || (max != null && desired > max)) {
        errors.rejectValue "capacity", "${context}.desired.capacity.not.in.range",
          [min, max, desired] as String[],
          "desired capacity (${desired}) is not within min/max (${min}/${max}) range"
      }
    }
  }
  
  def validateLimit(Errors errors, String value, int limit, String attribute) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${attribute}.empty")
      return false
    } else if (value.length() >= limit) {
      errors.rejectValue(attribute, "${context}.${attribute}.exceedsLimit")
    }
    return true
  }

  def validateNotNull(Errors errors, Object value, String attribute) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${attribute}.null")
      return false
    }
    return true
  }
}
