/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.UpsertOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.apache.commons.net.util.SubnetUtils
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors

import static UpsertOpenstackSecurityGroupDescription.Rule

/**
 * TODO most of the validate methods can be moved into base class,
 * since other drivers are doing the same thing.
 */
class OpenstackAttributeValidator {
  static final namePattern = /^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$/
  static final prefixPattern = /^[a-z0-9]+$/

  String context
  Errors errors

  OpenstackAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  static final maxPort = (1 << 16) - 1

  boolean validateByRegex(String value, String attribute, String regex) {
    def result
    if (value ==~ regex) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${regex})")
      result = false
    }
    result
  }

  boolean validateByContainment(Object value, String attribute, List<Object> list) {
    def result
    if (list.contains(value)) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must be one of $list)")
      result = false
    }
    result
  }

  void reject(String attribute, String reason) {
    errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid ($reason)")
  }

  def validatePort(int port, String attribute) {
    def result = (port >= 1 && port <= maxPort)
    if (!result) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must be in range [1, $maxPort])")
    }
    result
  }

  boolean validateNotNull(Object obj, String attribute) {
    boolean  result = true
    if (!obj) {
      result = false
      reject(attribute, 'null')
    }
    result
  }

  boolean validateNotEmpty(Object value, String attribute) {
    def result
    if (value != "" && value != null && value != []) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      result = false
    }
    result
  }

  boolean validateNotEmpty(List value, String attribute) {
    def result
    if (value != null && value.size() > 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      result = false
    }
    result
  }

  boolean validateNonNegative(Integer value, String attribute) {
    def result
    if (value != null && value >= 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.negative")
      result = false
    }
    result
  }

  boolean validatePositive(Integer value, String attribute) {
    def result
    if (value != null && value > 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notPositive")
      result = false
    }
    result
  }

  boolean validateGreaterThan(Integer subject, Integer other, String attribute) {
    def result
    if (subject != null && other != null && subject > other) {
      result = true
    }
    else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notGreaterThan")
      result = false
    }
    result
  }

  boolean validateGreaterThanEqual(Integer subject, Integer other, String attribute) {
    def result
    if (subject != null && other != null && subject >= other) {
      result = true
    }
    else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notGreaterThan")
      result = false
    }
    result
  }

  def validateApplication(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, prefixPattern)
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${prefixPattern})")
      return false
    }
  }

  def validateStack(String value, String attribute) {
    // Stack is optional
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, prefixPattern)
    }
  }

  def validateDetails(String value, String attribute) {
    // Details are optional.
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, namePattern)
    }
  }

  /**
   * Validate credentials.
   * @param credentials
   * @param accountCredentialsProvider
   * @return
   */
  def validateCredentials(String account, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(account, "account")
    if (result) {
      def openstackCredentials = accountCredentialsProvider.getCredentials(account)
      if (!(openstackCredentials?.credentials instanceof OpenstackCredentials)) {
        errors.rejectValue("${context}.account", "${context}.account.notFound")
        result = false
      }
    }
    result
  }

  /**
   * Validate string is in UUID format.
   * @param value
   * @param attribute
   * @return
   */
  def validateUUID(String value, String attribute) {
    boolean result = validateNotEmpty(value, attribute)
    if (result) {
      try {
        UUID.fromString(value)
        result = true
      } catch (IllegalArgumentException e) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notUUID")
        result = false
      }
    }
    result
  }

  /**
   * Validate string is in CIDR format.
   * @param value
   * @param attribute
   * @return
   */
  def validateCIDR(String value, String attribute) {
    boolean result = validateNotEmpty(value, attribute)
    if (result) {
      try {
        new SubnetUtils(value)
      } catch (IllegalArgumentException e) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalidCIDR")
        result = false
      }
    }
    result
  }

  def validateRuleType(String value, String attribute) {
    validateNotEmpty(value, attribute) &&
      validateByContainment(value, attribute, [Rule.RULE_TYPE_TCP])
  }

  /**
   * Validate integer is an HTTP status code
   * @param value
   * @param attribute
   * @return
   */
  def validateHttpStatusCode(Integer value, String attribute) {
    boolean result = validateNotEmpty(value, attribute)
    if (result) {
      try {
        HttpStatus.valueOf(value)
      } catch (IllegalArgumentException e) {
        reject(attribute, 'invalid Http Status Code')
        result = false
      }
    }
    result
  }

  /**
   * Validate string is an HTTP method
   * @param value
   * @param attribute
   * @return
   */
  def validateHttpMethod(String value, String attribute) {
    boolean result = validateNotEmpty(value, attribute)
    if (result) {
      try {
        HttpMethod.valueOf(value)
      } catch (IllegalArgumentException e) {
        reject(attribute, 'invalid Http Method')
        result = false
      }
    }
    result
  }

  /**
   * Validate string is a URI
   * @param value
   * @param attribute
   * @return
   */
  def validateURI(String value, String attribute) {
    boolean result = validateNotEmpty(value, attribute)

    try {
      URI.create(value)
    } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
      result = false
      reject(attribute, 'invalid URL')
    }
    result
  }

  /**
   * Validate the region
   * @param region
   * @param credentials
   * @return
   */
  def validateRegion(String region, OpenstackClientProvider provider) {
    boolean result = validateNotEmpty(region, 'region')
    if (result) {
      result = provider?.allRegions?.contains(region)
      if (!result) {
        reject('region', 'invalid region')
      }
    }
    result
  }
}
