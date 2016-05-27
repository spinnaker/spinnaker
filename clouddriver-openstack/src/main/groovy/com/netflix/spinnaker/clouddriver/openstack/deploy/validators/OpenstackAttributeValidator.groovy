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

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors

/**
 * TODO most of the validate methods can be moved into base class,
 * since other drivers are doing the same thing.
 */
class OpenstackAttributeValidator {

  String context
  Errors errors

  OpenstackAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors  }

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

  boolean validateNotEmpty(Object value, String attribute) {
    def result
    if (value != "" && value != null && value != []) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}",  "${context}.${attribute}.empty")
      result = false
    }
    result
  }

  boolean validateNonNegative(int value, String attribute) {
    def result
    if (value >= 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.negative")
      result = false
    }
    result
  }

  boolean validatePositive(int value, String attribute) {
    def result
    if (value > 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notPositive")
      result = false
    }
    result
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
        errors.rejectValue("${context}.account",  "${context}.account.notFound")
        result = false
      }
    }
    result
  }

}
