/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.kato.azure.deploy.validators

import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors

class StandardAzureAttributeValidator {
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
  StandardAzureAttributeValidator(String context, Errors errors) {
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
   * @see this.validateNotEmptyAsPart
   */
  def validateNotEmpty(Object value, String attribute) {
    validateNotEmptyAsPart(value, attribute, attribute)
  }

  /**
   * Validate a value that should not be empty.
   *
   * @param value The value cannot be null, empty string, or an empty container.
   *              However it can be a container of only empty values.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNotEmptyAsPart(Object value, String attribute, String part) {
    if (!value) {
      errors.rejectValue(attribute, "${context}.${part}.empty")
      return false
    }
    return true
  }

  /**
   * Validates {@code value} as a valid generic GCE name.
   *
   * @param value The name being validated.
   *              However it can be a container of only empty values.
   * @param attribute The attribute and part names are the same, meaning this is
   *              suitable for 'primitive' values or composite types treated as a whole.
   *
   * @see this.validateNameAsPart()
   */
  def validateName(String value, String attribute) {
    validateNameAsPart(value, attribute, attribute)
  }

  /**
   * Validates {@code value} as a valid generic Azure name.
   *
   * Specific resource types may have more specific validation rules.
   * This validator is just treating {@code value} as a generic Azure name,
   * which cannot be empty and may have additional constraints (symbols, etc).
   *
   * @param value The name being validated.
   * @param attribute The name of the attribute being validated.
   * @param part If different than the attribute name then this is a subcomponent
   *             within the attribute (e.g. element within a list).
   */
  def validateNameAsPart(String value, String attribute, String part) {
    def result = validateNotEmptyAsPart(value, attribute, part)

    return result
  }

  def validateCredentials(AzureCredentials account, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(account.appKey, "credentials")
    if (result) {
      def credentials = account

      if (!(credentials instanceof AzureCredentials)) {
        errors.rejectValue("credentials", "${context}.credentials.invalid")
        result = false
      }
    }
    return result
  }

  def validateRegion(String region) {
    validateNotEmpty(region, "region")
  }

  def validateNetwork(String vnet) {
    validateNotEmpty(vnet, "network")
  }

  def validateNonNegativeInt(int value, String attribute) {
    def result = true
    if (value < 0) {
      errors.rejectValue attribute, "${context}.${attribute}.negative"
      result = false
    }
    return result
  }

  def validateServerGroupName(String serverGroupName) {
    validateName(serverGroupName, "serverGroupName")
  }

  def validateImage(String image) {
    validateNotEmpty(image, "image")
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

  def validateInstanceType(String instanceType) {
    validateNotEmpty(instanceType, "instanceType")
  }

  def validateTags(List<String> tags) {
    return validateOptionalNameList(tags, "tag")
  }
}
