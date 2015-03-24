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

package com.netflix.spinnaker.kato.gce.deploy.validators

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
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

  // TODO(duftler): Also validate against set of supported GCE regions.
  def validateRegion(String region) {
    validateNotEmpty(region, "region")
  }

  // TODO(duftler): Also validate against set of supported GCE zones.
  def validateZone(String zone) {
    validateNotEmpty(zone, "zone")
  }

  def validateNonNegativeInt(int value, String attribute) {
    def result = true
    if (value < 0) {
      errors.rejectValue attribute, "${context}.${attribute}.negative"
      result = false
    }
    return result
  }

  def validateReplicaPoolName(String replicaPoolName) {
    validateName(replicaPoolName, "replicaPoolName")
  }

  // TODO(duftler): Also validate against set of supported GCE images.
  def validateImage(String image) {
    validateNotEmpty(image, "image")
  }

  def validateInstanceIds(List<String> instanceIds) {
    def result = validateNotEmpty(instanceIds, "instanceIds")
    instanceIds.eachWithIndex { value, index ->
      result &= validateNameAsPart(value, "instanceIds", "instanceId$index")
    }
    return result
  }

  def validateInstanceName(String instanceName) {
    validateName(instanceName, "instanceName")
  }

  // TODO(duftler): Also validate against set of supported GCE types.
  def validateInstanceType(String instanceType) {
    validateNotEmpty(instanceType, "instanceType")
  }
}
