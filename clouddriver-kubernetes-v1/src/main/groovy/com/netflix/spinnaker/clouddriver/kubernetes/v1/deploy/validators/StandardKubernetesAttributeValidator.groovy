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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators

import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.apache.http.conn.util.InetAddressUtils
import org.springframework.validation.Errors

class StandardKubernetesAttributeValidator {
  static final namePattern = /^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$/
  static final dnsSubdomainPattern = /^[a-z0-9]+([-\.a-z0-9]*[a-z0-9])?$/
  static final credentialsPattern = /^[a-z0-9]+([-a-z0-9_]*[a-z0-9])?$/
  static final prefixPattern = /^[a-z0-9]+$/
  static final httpPathPattern = /^\/.*$/
  static final unixPathPattern = /^\/.*$/
  static final winPathPattern = /^[a-zA-Z]:(\\|\/).*$/
  static final quantityPattern = /^([+-]?[0-9.]+)([eEimkKMGTP]*[-+]?[0-9]*)$/
  static final protocolList = ['TCP', 'UDP']
  static final serviceTypeList = ['ClusterIP', 'NodePort', 'LoadBalancer']
  static final sessionAffinityList = ['None', 'ClientIP']
  static final restartPolicyList = ['Always', 'OnFailure', 'Never']
  static final uriSchemeList = ['HTTP', 'HTTPS']
  static final maxPort = (1 << 16) - 1

  String context

  Errors errors

  StandardKubernetesAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  def validateByRegex(String value, String attribute, String regex) {
    def result
    if (value ==~ regex) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${regex})")
      result = false
    }
    result
  }

  def validateByContainment(Object value, String attribute, List<Object> list) {
    def result
    if (list.contains(value)) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must be one of $list)")
      result = false
    }
    result
  }

  def reject(String attribute, String reason) {
    errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid ($reason)")
  }

  def validateDetails(String value, String attribute) {
    // Details are optional.
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, namePattern)
    }
  }

  def validateName(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, namePattern)
    } else {
      return false
    }
  }

  def validateSecretName(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, dnsSubdomainPattern)
    } else {
      return false
    }
  }

  def validatePath(String value, String attribute) {
    def result
    if (validateNotEmpty(value, attribute)) {
      if (value ==~ unixPathPattern || value ==~ winPathPattern) {
        result = true
      } else {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${unixPathPattern} or ${winPathPattern})")
        result = false
      }
    } else {
      result = false
    }
    result
  }

  def validateHttpPath(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, httpPathPattern)
    } else {
      return false
    }
  }

  def validateRelativePath(String value, String attribute) {
    def result
    if (validateNotEmpty(value, attribute)) {
      if (value ==~ unixPathPattern || value ==~ winPathPattern) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must not match ${unixPathPattern} or ${winPathPattern})")
        result = false
      } else {
        result = true
      }
    } else {
      result = false
    }
    result
  }

  def validateProtocol(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByContainment(value, attribute, protocolList)
    } else {
      return false
    }
  }

  def validateSessionAffinity(String value, String attribute) {
    value ? validateByContainment(value, attribute, sessionAffinityList) : null
  }

  def validateUriScheme(String value, String attribute) {
    value ? validateByContainment(value, attribute, uriSchemeList) : null
  }

  def validateIpv4(String value, String attribute) {
    def result = InetAddressUtils.isIPv4Address(value)
    if (!result) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Not valid IPv4 address)")
    }
    result
  }

  def validateServiceType(String value, String attribute) {
    value ? validateByContainment(value, attribute, serviceTypeList) : true
  }

  def validatePort(int port, String attribute) {
    def result = (port >= 1 && port <= maxPort)
    if (!result) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must be in range [1, $maxPort])")
    }
    result
  }

  def validateApplication(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, prefixPattern)
    } else {
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

  def validateCpu(String value, String attribute) {
    // CPU is optional.
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, quantityPattern)
    }
  }

  def validateMemory(String value, String attribute) {
    // Memory is optional.
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, quantityPattern)
    }
  }

  def validateImagePullSecret(KubernetesV1Credentials credentials, String value, String namespace, String attribute) {
    if (!credentials.isRegisteredImagePullSecret(value, namespace)) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notRegistered")
      return false
    }
    return validateByRegex(value, attribute, namePattern)
  }

  def validateNamespace(KubernetesV1Credentials credentials, String value, String attribute) {
    // Namespace is optional, empty taken to mean 'default'.
    if (!value) {
      return true
    } else {
      if (!credentials.isRegisteredNamespace(value)) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notRegistered")
        return false
      }
      return validateByRegex(value, attribute, namePattern)
    }
  }

  def validateNotEmpty(Object value, String attribute) {
    def result
    if (value != "" && value != null && value != []) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}",  "${context}.${attribute}.empty")
      result = false
    }
    result
  }

  def validateCredentials(String credentials, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(credentials, "account")
    if (result) {
      def kubernetesCredentials = accountCredentialsProvider.getCredentials(credentials)
      if (!(kubernetesCredentials?.credentials instanceof KubernetesV1Credentials)) {
        errors.rejectValue("${context}.account",  "${context}.account.notFound")
        result = false
      }
    }
    result
  }

  def validateNonNegative(int value, String attribute) {
    def result
    if (value >= 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.negative")
      result = false
    }
    result
  }

  def validatePositive(int value, String attribute) {
    def result
    if (value > 0) {
      result = true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notPositive")
      result = false
    }
    result
  }

  def validateRestartPolicy(String value, String attribute) {
    value ? validateByContainment(value, attribute, restartPolicyList) : null
  }

  def validateJobCloneSource(Object value, String attribute) {
    if (!value) {
      errors.rejectValue("${context}.${attribute}",  "${context}.${attribute}.empty")
      return false
    } else {
      return validateNotEmpty(value.jobName, attribute)
    }
  }

  def validateServerGroupCloneSource(Object value, String attribute) {
    if (!value) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      return false
    } else {
      return validateNotEmpty(value.serverGroupName, attribute)
    }
  }

  def validateNotLessThan(Integer value1, Integer value2, String attribute1, String attribute2) {
    if (value1 < value2) {
      errors.rejectValue("${context}.${attribute1}", "${context}.${attribute1}.lessThan ${context}.${attribute2}")
    }
  }

  def validateInRangeInclusive(Integer value, int min, int max, String attribute) {
    if (min > value) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.greaterThan $min")
    }

    if (max < value) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.lessThan $max")
    }
  }
}
