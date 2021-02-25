/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentialType
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentials
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineModelUtil
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ScalingPolicyType
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineInstanceProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.credentials.CredentialsRepository

class StandardAppengineAttributeValidator {
  static final namePattern = /^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$/
  static final prefixPattern = /^[a-z0-9]+$/

  String context
  ValidationErrors errors

  StandardAppengineAttributeValidator(String context, ValidationErrors errors) {
    this.context = context
    this.errors = errors
  }

  def validateCredentials(String credentials, CredentialsRepository<AppengineNamedAccountCredentials> credentialsRepository) {
    def result = validateNotEmpty(credentials, "account")
    if (result) {
      def appengineCredentials = credentialsRepository.getOne(credentials)
      if (!appengineCredentials) {
        errors.rejectValue("${context}.account",  "${context}.account.notFound")
        result = false
      }
    }
    result
  }

  def validateGitCredentials(AppengineGitCredentials gitCredentials,
                             AppengineGitCredentialType gitCredentialType,
                             String accountName,
                             String attribute) {
    if (validateNotEmpty(gitCredentialType, attribute)) {
      def supportedCredentialTypes = gitCredentials.getSupportedCredentialTypes()
      def credentialTypeSupported = supportedCredentialTypes.contains(gitCredentialType)
      if (credentialTypeSupported) {
        return true
      } else {
        errors.rejectValue("${context}.${attribute}",  "${context}.${attribute}.invalid" +
                           " (Account ${accountName} supports only the following git credential types: ${supportedCredentialTypes.join(", ")}")
        return false
      }
    } else {
      return false
    }
  }

  def validateNotEmpty(Object value, String attribute) {
    if (value != "" && value != null && value != []) {
      return true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      return false
    }
  }

  def validateApplication(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, prefixPattern)
    } else {
      return false
    }
  }

  def validateStack(String value, String attribute) {
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, prefixPattern)
    }
  }

  def validateDetails(String value, String attribute) {
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, namePattern)
    }
  }

  def validateByRegex(String value, String attribute, String regex) {
    if (value ==~ regex) {
      return true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${regex})")
      return false
    }
  }

  def validateTrafficSplit(AppengineTrafficSplit trafficSplit, String attribute) {
    if (validateNotEmpty(trafficSplit, attribute)) {
      if (trafficSplit.allocations) {
        return validateAllocations(trafficSplit.allocations, trafficSplit.shardBy, attribute + ".allocations")
      } else if (trafficSplit.shardBy) {
        return true
      } else {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
        return false
      }
    } else {
      return false
    }
  }

  def validateMaxNotLessThanMin(Integer minValue, Integer maxValue, String minAttribute, String maxAttribute) {
    if (maxValue < minValue) {
      errors.rejectValue(maxAttribute, "${context}.${maxAttribute} must not be less than ${context}.${minAttribute}.")
      return false
    } else {
      return true
    }
  }

  def validateNonNegative(Integer value, String attribute) {
    if (value && value < 0) {
      errors.rejectValue(attribute, "${context}.${attribute}.negative")
      return false
    } else {
      return true
    }
  }

  def validateAllocations(Map<String, Double> allocations, ShardBy shardBy, String attribute) {
    def decimalPlaces = shardBy == ShardBy.COOKIE ?
      AppengineModelUtil.COOKIE_SPLIT_DECIMAL_PLACES :
      AppengineModelUtil.IP_SPLIT_DECIMAL_PLACES

    def serverGroupsWithInvalidAllocations = []
    allocations.each { serverGroupName, allocation ->
      if ((allocation as Double).round(decimalPlaces) != allocation) {
        serverGroupsWithInvalidAllocations << serverGroupName
      }
    }

    if (serverGroupsWithInvalidAllocations) {
      def pluralize = serverGroupsWithInvalidAllocations.size() > 1
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid "
                         + "(Allocation${pluralize ? "s" : ""} invalid for ${serverGroupsWithInvalidAllocations.join(", ")}. "
                         + "Allocations for shard type ${shardBy.toString()} can have up to $decimalPlaces decimal places.)")
      return false
    }

    if ((allocations.collect { k, v -> v }.sum() as Double).round(decimalPlaces) != 1) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Allocations must sum to 1)")
      return false
    } else {
      return true
    }
  }

  def validateServerGroupsCanBeEnabled(Collection<String> serverGroupNames,
                                       String loadBalancerName,
                                       AppengineNamedAccountCredentials credentials,
                                       AppengineClusterProvider appengineClusterProvider,
                                       String attribute) {
    def rejectedServerGroups = serverGroupNames.inject([:].withDefault { [] }, { Map reject, serverGroupName ->
      def serverGroup = appengineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
      if (!serverGroup) {
        reject.notFound << serverGroupName
        return reject
      } else if (loadBalancerName && serverGroup?.loadBalancers.contains(loadBalancerName) != true ) {
        reject.notRegisteredWithLoadBalancer << serverGroupName
        return reject
      } else {
        return reject
      }
    })

    def valid = true
    if (rejectedServerGroups.notFound) {
      def notFound = rejectedServerGroups.notFound
      errors.rejectValue(
        "${context}.${attribute}",
        "${context}.${attribute}.invalid (Server group${notFound.size() > 1 ? "s" : ""} ${notFound.join(", ")} not found)."
      )

      valid = false
    }

    if (rejectedServerGroups.notRegisteredWithLoadBalancer) {
      def notRegistered = rejectedServerGroups.notRegisteredWithLoadBalancer
      errors.rejectValue(
        "${context}.${attribute}",
        "${context}.${attribute}.invalid (Server group${notRegistered.size() > 1 ? "s" : ""} ${notRegistered.join(", ")} " +
          "not registered with load balancer $loadBalancerName)."
      )

      valid = false
    }

    return valid
  }

  def validateLoadBalancerCanBeDeleted(String loadBalancerName, String attribute) {
    if (loadBalancerName == "default") {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Cannot delete default service).")
      return false
    } else {
      return true
    }
  }

  def validateInstances(List<String> instanceIds,
                        AppengineNamedAccountCredentials credentials,
                        AppengineInstanceProvider appengineInstanceProvider,
                        String attribute) {
    def instances = instanceIds.collect { appengineInstanceProvider.getInstance(credentials.name, credentials.region, it) }
    def valid = true

    instances.eachWithIndex { AppengineInstance instance, int i ->
      def name = instanceIds[i]
      if (!instance) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Instance $name not found).")
        valid = false
        return null
      }

      if (!instance.serverGroup) {
        errors.rejectValue("${context}.${attribute}",
                           "${context}.${attribute}.invalid (Could not find parent server group for instance $name).")
        valid = false
      }

      if (!instance.loadBalancers?.getAt(0)) {
        errors.rejectValue("${context}.${attribute}",
                           "${context}.${attribute}.invalid (Could not find parent load balancer for instance $name).")
        valid = false
      }
    }

    return valid
  }

  def validateServerGroupCanBeDisabled(String serverGroupName,
                                       AppengineNamedAccountCredentials credentials,
                                       AppengineClusterProvider clusterProvider,
                                       AppengineLoadBalancerProvider loadBalancerProvider,
                                       String attribute) {
    def serverGroup = clusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def valid = true
    if (!serverGroup) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Server group $serverGroupName not found).")
      valid = false
      return valid
    }

    def loadBalancerName = serverGroup?.loadBalancers?.first()
    def loadBalancer = loadBalancerProvider.getLoadBalancer(credentials.name, loadBalancerName)
    if (!loadBalancer) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Could not find parent load balancer $loadBalancerName for server group $serverGroupName).")
      valid = false
      return valid
    }

    if (loadBalancer?.split?.allocations?.get(serverGroupName) == 1 as Double) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Server group $serverGroupName is the only server group " +
                         "receiving traffic from load balancer $loadBalancerName).")
      valid = false
    }
    return valid
  }

  def validateServingStatusCanBeChanged(String serverGroupName,
                                        AppengineNamedAccountCredentials credentials,
                                        AppengineClusterProvider clusterProvider,
                                        String attribute) {
    def serverGroup = clusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    if (!serverGroup) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Server group $serverGroupName not found).")
      return false
    }

    def isFlex = serverGroup.env == AppengineServerGroup.Environment.FLEXIBLE
    def usesManualScaling = serverGroup.scalingPolicy?.type == ScalingPolicyType.MANUAL
    def usesBasicScaling = serverGroup.scalingPolicy?.type == ScalingPolicyType.BASIC

    if (!(isFlex || usesBasicScaling || usesManualScaling)) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Only server groups that use the App Engine flexible environment," +
                         " or use basic or manual scaling can be started or stopped).")
      return false
    } else {
      return true
    }
  }

  def validateShardBy(AppengineTrafficSplit split, Boolean migrateTraffic, String attribute) {
    if (!split) {
      return true
    }

    if (migrateTraffic && !validateNotEmpty(split.shardBy, attribute)) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (A shardBy value must be specified for gradual traffic migration).")
      return false
    }

    def targetNumberOfEnabledServerGroups = split.allocations?.keySet()?.size() ?: 0
    if (targetNumberOfEnabledServerGroups > 1 && !validateNotEmpty(split.shardBy, attribute)) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (A shardBy value must be specified if traffic " +
                         "will be split between multiple server groups).")
      return false
    }

    return true
  }

  def validateGradualMigrationIsAllowed(AppengineTrafficSplit split,
                                        AppengineNamedAccountCredentials credentials,
                                        AppengineClusterProvider clusterProvider,
                                        String attribute) {
    if (!validateNotEmpty(split.allocations, "split.allocations")) {
      return false
    }

    if (split.allocations.keySet().size() > 1) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid " +
                         "(Cannot gradually migrate traffic to multiple server groups).")
      return false
    }

    def serverGroup = clusterProvider.getServerGroup(credentials.name, credentials.region, split.allocations.keySet()[0])
    if (!serverGroup.allowsGradualTrafficMigration) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid " +
                         "(Cannot gradually migrate traffic to this server group. " +
                         "Gradual migration is allowed only for server groups in the App Engine standard " +
                         "environment that use automatic scaling and have warmup requests enabled).")
      return false
    }

    return true
  }

  def validateServerGroupSupportsAutoscalingPolicyUpsert(String serverGroupName,
                                                         AppengineNamedAccountCredentials credentials,
                                                         AppengineClusterProvider clusterProvider,
                                                         String attribute) {
    if (!serverGroupName) {
      return
    }

    def serverGroup = clusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    if (serverGroup) {
      // App Engine standard versions don't always have an 'env' property.
      def isStandard = serverGroup.env != AppengineServerGroup.Environment.FLEXIBLE
      def usesAutomaticScaling = serverGroup.scalingPolicy?.type == ScalingPolicyType.AUTOMATIC
      if (isStandard && usesAutomaticScaling) {
        return true
      } else {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid " +
                           "(Autoscaling policies can only be updated for " +
                           "server groups in the App Engine standard environment that use automatic scaling).")
        return false
      }
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.notFound " +
                         "(Cannot find server group $serverGroupName.)")
      return false
    }
  }
}
