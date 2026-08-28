/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import groovy.util.logging.Slf4j

@Slf4j
class LoadBalancerV2UpsertHandler {

  private static final String BASE_PHASE = "UPSERT_ELB_V2"

  private static final String ATTRIBUTE_IDLE_TIMEOUT = "idle_timeout.timeout_seconds"
  private static final String ATTRIBUTE_DELETION_PROTECTION = "deletion_protection.enabled"
  private static final String ATTRIBUTE_LOAD_BALANCING_CROSS_ZONE = "load_balancing.cross_zone.enabled"

  //Defaults for Target Group Attributes
  private static final String DEREGISTRATION_DELAY = "300"
  private static final Boolean STICKINESS_ENABLED = false
  private static final String STICKINESS_TYPE = "lb_cookie"
  private static final String STICKINESS_DURATION = "86400"
  private static final Boolean PROXY_PROTOCOL_V2 = false
  private static final Boolean CONNECTION_TERMINATION = false
  /** The following attribute is supported only if the target is a Lambda function. */
  private static final Boolean MULTI_VALUE_HEADERS_ENABLED = false

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  //Create Target Group Attributes with values provided in description, set to defaults other wise
  static String createTargetGroupAttributes(ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, TargetGroup targetGroup, UpsertAmazonLoadBalancerV2Description.Attributes attributes, DeployDefaults deployDefaults) {
    def targetGroupAttributes = []
    log.info("Creating target group attributes for targetGroup {}", targetGroup.targetGroupName())
    if (attributes) {
      if (TargetTypeEnum.LAMBDA.toString().equalsIgnoreCase(targetGroup.targetTypeAsString())) {
        def multiValueHeaderAttribute = attributes.multiValueHeadersEnabled ?: MULTI_VALUE_HEADERS_ENABLED
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("lambda.multi_value_headers.enabled").value(multiValueHeaderAttribute.toString()).build())

      } else {
        Integer deregistrationDelay = [attributes.deregistrationDelay, deployDefaults?.loadBalancing?.deregistrationDelayDefault].findResult(Closure.IDENTITY)

        def deregistrationDealyAttribute = deregistrationDelay?.toString() ?: DEREGISTRATION_DELAY
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("deregistration_delay.timeout_seconds").value(deregistrationDealyAttribute.toString()).build())
      }
      if (loadBalancer.typeAsString() == 'application') {
        def stickinessEnabledAttribute = attributes.stickinessEnabled?.toString() ?: STICKINESS_ENABLED
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.enabled").value(stickinessEnabledAttribute.toString()).build())

        def stickinessTypeAttribute = attributes.stickinessType ?: STICKINESS_TYPE
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.type").value(stickinessTypeAttribute.toString()).build())

        def stickinessDurationAttribute = attributes.stickinessDuration?.toString() ?: STICKINESS_DURATION
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.lb_cookie.duration_seconds").value(stickinessDurationAttribute.toString()).build())

      }
      if (loadBalancer.typeAsString() == 'network') {
        def proxyProtocolV2Attribute = attributes.proxyProtocolV2 ?: PROXY_PROTOCOL_V2
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("proxy_protocol_v2.enabled").value(proxyProtocolV2Attribute.toString()).build())

        def enableConnectionTermination = attributes.deregistrationDelayConnectionTermination ?: CONNECTION_TERMINATION
        targetGroupAttributes.add(TargetGroupAttribute.builder().key("deregistration_delay.connection_termination.enabled").value(enableConnectionTermination.toString()).build())

      }
    }
    return updateTargetGroupAttributes(loadBalancing, targetGroup, targetGroupAttributes)
  }

  // Modify target group attributes with attributes that are set in the description , do not update attributes that are not set
  private static String modifyTargetGroupAttributes(ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, TargetGroup targetGroup, UpsertAmazonLoadBalancerV2Description.Attributes attributes, DeployDefaults deployDefaults) {

    log.info("Update target group attributes for targetGroup {}", targetGroup.targetGroupName())
    def targetGroupAttributes = []
    if (attributes) {
      if (TargetTypeEnum.LAMBDA.toString().equalsIgnoreCase(targetGroup.targetTypeAsString())) {
        if (attributes.multiValueHeadersEnabled != null) {
          targetGroupAttributes.add(TargetGroupAttribute.builder().key("lambda.multi_value_headers.enabled").value(attributes.multiValueHeadersEnabled.toString()).build())
        }
      } else {
        Integer deregistrationDelay = [attributes.deregistrationDelay, deployDefaults?.loadBalancing?.deregistrationDelayDefault].findResult(Closure.IDENTITY)
        if (deregistrationDelay != null) {
          targetGroupAttributes.add(TargetGroupAttribute.builder().key("deregistration_delay.timeout_seconds").value(deregistrationDelay.toString()).build())
        }
        if (loadBalancer.typeAsString() == 'application') {
          if (attributes.stickinessEnabled != null) {
            targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.enabled").value(attributes.stickinessEnabled.toString()).build())
          }
          if (attributes.stickinessType != null) {
            targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.type").value(attributes.stickinessType).build())
          }
          if (attributes.stickinessDuration != null) {
            targetGroupAttributes.add(TargetGroupAttribute.builder().key("stickiness.lb_cookie.duration_seconds").value(attributes.stickinessDuration.toString()).build())
          }
        }
        if (loadBalancer.typeAsString() == 'network') {
          if (attributes.proxyProtocolV2 != null) {
            targetGroupAttributes.add(TargetGroupAttribute.builder().key("proxy_protocol_v2.enabled").value(attributes.proxyProtocolV2.toString()).build())
          }

          if(attributes.deregistrationDelayConnectionTermination != null) {
            targetGroupAttributes.add(TargetGroupAttribute.builder().key("deregistration_delay.connection_termination.enabled").value(attributes.deregistrationDelayConnectionTermination.toString()).build())
          }

        }
      }
    }
    return updateTargetGroupAttributes(loadBalancing, targetGroup, targetGroupAttributes)
  }

  static String updateTargetGroupAttributes(ElasticLoadBalancingV2Client loadBalancing, TargetGroup targetGroup, List<TargetGroupAttribute> targetGroupAttributes) {
    if (!targetGroupAttributes.isEmpty()) {
      try {
        loadBalancing.modifyTargetGroupAttributes(ModifyTargetGroupAttributesRequest.builder()
          .targetGroupArn(targetGroup.targetGroupArn())
          .attributes(targetGroupAttributes)
          .build())
        task.updateStatus BASE_PHASE, "Modified target group ${targetGroup.targetGroupName()} attributes."
      } catch (AwsServiceException e) {
        return handleError("Failed to modify attributes for target group ${targetGroup.targetGroupName()} - reason: ${e.toString()}.", e)
      }
    }
    return null
  }

  static List<TargetGroup> createTargetGroups(List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroupsToCreate, ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors, DeployDefaults deployDefaults) {
    String loadBalancerName = loadBalancer.loadBalancerName()
    List<TargetGroup> createdTargetGroups = new ArrayList<TargetGroup>()

    targetGroupsToCreate.each { targetGroup ->
      TargetGroup createdTargetGroup
      try {
        String status = "Target group created in ${loadBalancerName} (${targetGroup.name}:${targetGroup.port}:${targetGroup.protocol})."
        CreateTargetGroupRequest.Builder createTargetGroupRequest = CreateTargetGroupRequest.builder();
        if (TargetTypeEnum.LAMBDA.toString().equalsIgnoreCase(targetGroup.targetType)) {

          createTargetGroupRequest.name(targetGroup.name)
            .healthCheckIntervalSeconds(targetGroup.healthCheckInterval)
            .healthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
            .healthyThresholdCount(targetGroup.healthyThreshold)
            .unhealthyThresholdCount(targetGroup.unhealthyThreshold)
            .targetType(targetGroup.targetType)
            .matcher(Matcher.builder().httpCode(targetGroup.healthCheckMatcher).build())
            .healthCheckPath(targetGroup.healthCheckPath)

          status = "Lambda Target group created in ${loadBalancerName} (${targetGroup.name})."

        } else {
          createTargetGroupRequest.protocol(targetGroup.protocol)
            .port(targetGroup.port)
            .name(targetGroup.name)
            .vpcId(loadBalancer.vpcId())
            .healthCheckIntervalSeconds(targetGroup.healthCheckInterval)
            .healthCheckPort(targetGroup.healthCheckPort)
            .healthCheckProtocol(targetGroup.healthCheckProtocol)
            .healthyThresholdCount(targetGroup.healthyThreshold)
            .unhealthyThresholdCount(targetGroup.unhealthyThreshold)
            .targetType(targetGroup.targetType)

          if (targetGroup.healthCheckProtocol in [ProtocolEnum.HTTP, ProtocolEnum.HTTPS]) {
            createTargetGroupRequest
              .healthCheckPath(targetGroup.healthCheckPath)

            // HTTP(s) health checks for TCP does not support custom matchers and timeouts. Also, health thresholds must be equal.
            if (targetGroup.protocol == ProtocolEnum.TCP) {
              createTargetGroupRequest.unhealthyThresholdCount(targetGroup.healthyThreshold)
            } else {
              createTargetGroupRequest.matcher(Matcher.builder().httpCode(targetGroup.healthCheckMatcher).build())
                .healthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
            }
          }
        }
        CreateTargetGroupResponse createTargetGroupResult = loadBalancing.createTargetGroup( createTargetGroupRequest.build() )
        task.updateStatus BASE_PHASE, status
        createdTargetGroup = createTargetGroupResult.targetGroups().get(0)

      } catch (AwsServiceException e) {
        amazonErrors << handleError("Failed to create target group ${targetGroup.name} for ${loadBalancerName} - reason: ${e.toString()}.", e)
      }

      if (createdTargetGroup != null) {
        // Add the target group to existing target groups
        createdTargetGroups.add(createdTargetGroup)

        // Add attributes
        String exceptionMessage = createTargetGroupAttributes(loadBalancing, loadBalancer, createdTargetGroup, targetGroup.attributes, deployDefaults)
        if (exceptionMessage) {
          amazonErrors << exceptionMessage
        }
      }
    }

    return createdTargetGroups
  }

  static List<TargetGroup> removeTargetGroups(List<TargetGroup> targetGroupsToRemove, ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    List<TargetGroup> removedTargetGroups = new ArrayList<>()
    targetGroupsToRemove.each {
      try {
        loadBalancing.deleteTargetGroup(DeleteTargetGroupRequest.builder().targetGroupArn(it.targetGroupArn()).build())
        removedTargetGroups.push(it)
        task.updateStatus BASE_PHASE, "Target group removed from ${loadBalancer.loadBalancerName()} (${it.targetGroupName()}:${it.port()}:${it.protocol()})."
      } catch (ResourceInUseException e) {
        amazonErrors << handleError("Failed to delete target group ${it.targetGroupName()} from ${loadBalancer.loadBalancerName()} - reason: ${e.toString()}.", e)
      }
    }
    return removedTargetGroups
  }

  static void updateTargetGroups(List<TargetGroup> targetGroupsToUpdate, List<UpsertAmazonLoadBalancerV2Description.TargetGroup> updatedTargetGroups, ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    targetGroupsToUpdate.each { awsTargetGroup ->
      UpsertAmazonLoadBalancerV2Description.TargetGroup targetGroup = updatedTargetGroups.find({ it.name == awsTargetGroup.targetGroupName() })

      ModifyTargetGroupRequest.Builder modifyTargetGroupRequest = ModifyTargetGroupRequest.builder()
        .targetGroupArn(awsTargetGroup.targetGroupArn())
        .healthCheckIntervalSeconds(targetGroup.healthCheckInterval)
        .healthCheckPort(targetGroup.healthCheckPort)
        .healthCheckProtocol(targetGroup.healthCheckProtocol)
        .healthyThresholdCount(targetGroup.healthyThreshold)
        .unhealthyThresholdCount(targetGroup.unhealthyThreshold)

      if (targetGroup.healthCheckProtocol in [ProtocolEnum.HTTP, ProtocolEnum.HTTPS]) {
        modifyTargetGroupRequest
          .healthCheckPath(targetGroup.healthCheckPath)

        // HTTP(s) health checks for TCP does not support custom matchers and timeouts. Also, health thresholds must be equal.
        if (targetGroup.protocol == ProtocolEnum.TCP) {
          modifyTargetGroupRequest.unhealthyThresholdCount(targetGroup.healthyThreshold)
        } else {
          modifyTargetGroupRequest.matcher(Matcher.builder().httpCode(targetGroup.healthCheckMatcher).build())
            .healthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
        }
      }

      loadBalancing.modifyTargetGroup(modifyTargetGroupRequest.build())
      task.updateStatus BASE_PHASE, "Target group updated in ${loadBalancer.loadBalancerName()} (${awsTargetGroup.targetGroupName()}:${awsTargetGroup.port()}:${awsTargetGroup.protocol()})."

      // Update attributes
      String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, loadBalancer, awsTargetGroup, targetGroup.attributes, null)
      if (exceptionMessage) {
        amazonErrors << exceptionMessage
      }
    }
  }

  static boolean createListener(UpsertAmazonLoadBalancerV2Description.Listener listener, List<Action> defaultActions, List<Rule> rules, ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    CreateListenerResponse result
    try {
      result = loadBalancing.createListener(CreateListenerRequest.builder()
        .loadBalancerArn(loadBalancer.loadBalancerArn())
        .port(listener.port)
        .protocol(listener.protocol)
        .certificates(listener.certificates)
        .sslPolicy(listener.sslPolicy)
        .defaultActions(defaultActions)
        .build())
      task.updateStatus BASE_PHASE, "Listener added to ${loadBalancer.loadBalancerName()} (${listener.port}:${listener.protocol})."
    } catch (AwsServiceException e) {
      amazonErrors << handleError("Failed to add listener to ${loadBalancer.loadBalancerName()} (${listener.port}:${listener.protocol}) - reason: ${e.toString()}.", e)
      return false
    }

    if (result != null && result.listeners().size() > 0) {
      String listenerArn = result.listeners().get(0).listenerArn()
      try {
        rules.each { rule ->
          loadBalancing.createRule(CreateRuleRequest.builder().listenerArn(listenerArn).conditions(rule.conditions).actions(rule.actions).priority(Integer.valueOf(rule.priority)).build())
        }
      } catch (AwsServiceException e) {
        amazonErrors << handleError("Failed to add rule to listener ${loadBalancer.loadBalancerName()} (${listener.port}:${listener.protocol}) reason: ${e.toString()}.", e)
        return false
      }
    }

    return true
  }

  static boolean containsAllRules(List<Rule> aRules, List<Rule> bRules) {
    !aRules.any { aRule ->
      boolean foundMatchingRule = bRules.any { bRule ->
        bRule.actions().containsAll(aRule.actions()) && aRule.actions().containsAll(bRule.actions()) &&
          bRule.conditions().containsAll(aRule.conditions()) && aRule.conditions().containsAll(bRule.conditions()) &&
          bRule.priority() == aRule.priority()
      }
      return !foundMatchingRule
    }
  }

  static void updateListener(String listenerArn,
                             UpsertAmazonLoadBalancerV2Description.Listener listener,
                             List<Action> defaultActions, List<Rule> existingRules,
                             List<Rule> newRules,
                             ElasticLoadBalancingV2Client loadBalancing,
                             List<String> amazonErrors) {
    try {
      loadBalancing.modifyListener(ModifyListenerRequest.builder()
        .listenerArn(listenerArn)
        .protocol(listener.protocol)
        .certificates(listener.certificates)
        .sslPolicy(listener.sslPolicy)
        .defaultActions(defaultActions)
        .build())
      task.updateStatus BASE_PHASE, "Listener ${listenerArn} updated (${listener.port}:${listener.protocol})."
    } catch (AwsServiceException e) {
      amazonErrors << handleError("Failed to modify listener ${listenerArn} (${listener.port}:${listener.protocol}) - reason: ${e.toString()}.", e)
    }

    // Compare the old rules; if any are different, just replace them all.
    boolean rulesSame = existingRules.size() == newRules.size() &&
      containsAllRules(existingRules, newRules) &&
      containsAllRules(newRules, existingRules)

    if (!rulesSame) {
      existingRules.each { rule ->
        try {
          loadBalancing.deleteRule(DeleteRuleRequest.builder().ruleArn(rule.ruleArn()).build())
        } catch (AwsServiceException ignore) {
          // If the rule failed to be deleted, it could not be found, so we should be safe to create the new ones.
        }
      }
      newRules.each { rule ->
        try {
          loadBalancing.createRule(CreateRuleRequest.builder().listenerArn(listenerArn).conditions(rule.conditions()).actions(rule.actions()).priority(Integer.valueOf(rule.priority())).build())
        } catch (AwsServiceException e) {
          amazonErrors << handleError("Failed to add rule to listener ${listenerArn} (${listener.port}:${listener.protocol}) reason: ${e.toString()}.", e)
        }
      }
    }
  }

  static void removeListeners(List<Listener> listenersToRemove, List<Listener> existingListeners, ElasticLoadBalancingV2Client loadBalancing, LoadBalancer loadBalancer) {
    listenersToRemove.each {
      try {
        loadBalancing.deleteListener(DeleteListenerRequest.builder().listenerArn(it.listenerArn()).build())
        task.updateStatus BASE_PHASE, "Listener removed from ${loadBalancer.loadBalancerName()} (${it.port()}:${it.protocol()})."
        existingListeners.remove(it)
      } catch (ListenerNotFoundException e) {
        handleError("Failed to delete listener ${it.listenerArn()}. Listener could not be found. ${e.toString()}", e)
      }
    }
  }

  static List<Action> getAmazonActionsFromDescription(List<UpsertAmazonLoadBalancerV2Description.Action> actions, List<TargetGroup> existingTargetGroups, List<String> amazonErrors) {
    List<Action> awsActions = []
    actions.eachWithIndex { action, index ->
      if (action.type == "forward") {
        TargetGroup targetGroup = existingTargetGroups.find { it.targetGroupName() == action.targetGroupName }
        if (targetGroup != null) {
          Action awsAction = Action.builder().type(action.type).targetGroupArn(targetGroup.targetGroupArn()).order(index + 1).build()
          awsActions.add(awsAction)
        } else {
          String exceptionMessage = "Target group name ${action.targetGroupName} not found when trying to create action"
          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
        }
      } else if (action.type == "authenticate-oidc") {
        Action awsAction = Action.builder().type(action.type).authenticateOidcConfig(action.authenticateOidcActionConfig).order(index + 1).build()
        awsActions.add(awsAction)
      } else if (action.type == "redirect") {
        Action awsAction = Action.builder().type(action.type).redirectConfig(action.redirectActionConfig).order(index + 1).build()
        awsActions.add(awsAction)
      }
    }
    awsActions
  }

  static void updateLoadBalancer(ElasticLoadBalancingV2Client loadBalancing,
                                 LoadBalancer loadBalancer,
                                 Collection<String> securityGroups,
                                 List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                 List<UpsertAmazonLoadBalancerV2Description.Listener> listeners,
                                 DeployDefaults deployDefaults,
                                 Integer idleTimeout,
                                 Boolean deletionProtection,
                                 Boolean loadBalancingCrossZone,
                                 String ipAddressType
  ) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName()
    def loadBalancerArn = loadBalancer.loadBalancerArn()

    if (loadBalancer.typeAsString() == 'application') {
      if (loadBalancer.vpcId() && !securityGroups) {
        throw new IllegalArgumentException("Load balancer ${loadBalancerName} must have at least one security group")
      }

      if (securityGroups) {
        loadBalancing.setSecurityGroups(SetSecurityGroupsRequest.builder()
          .loadBalancerArn(loadBalancerArn)
          .securityGroups(securityGroups)
          .build())
        task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."
      }
    }

    def currentIpAddressType = loadBalancer.ipAddressTypeAsString()
    if (ipAddressType && ipAddressType != currentIpAddressType && (loadBalancer.typeAsString() == 'application' || loadBalancer.typeAsString() == 'network')) {
      def newIpAddressType = loadBalancer.schemeAsString() == 'internal' ? 'ipv4' : ipAddressType
       loadBalancing.setIpAddressType(SetIpAddressTypeRequest.builder()
         .loadBalancerArn(loadBalancerArn)
         .ipAddressType(newIpAddressType)
         .build())
      task.updateStatus BASE_PHASE, "IP Address type updated ${loadBalancerName}."
    }

    // Update load balancer attributes
    def currentAttributes = loadBalancing.describeLoadBalancerAttributes(
      DescribeLoadBalancerAttributesRequest.builder()
        .loadBalancerArn(loadBalancerArn)
        .build()
    ).attributes()

    List<LoadBalancerAttribute> attributes = []

    // idle timeout is only supported in application load balancers
    if (loadBalancer.typeAsString() == 'application') {
      String currentIdleTimeout = currentAttributes.find { it.key() == ATTRIBUTE_IDLE_TIMEOUT }?.value()
      String newIdleTimeout = [idleTimeout, deployDefaults.loadBalancing.idleTimeout].findResult(Closure.IDENTITY).toString()
      if (currentIdleTimeout != newIdleTimeout) {
        task.updateStatus BASE_PHASE, "Setting idle timeout on ${loadBalancerName} to ${newIdleTimeout}."
        attributes.add(LoadBalancerAttribute.builder().key(ATTRIBUTE_IDLE_TIMEOUT).value(newIdleTimeout).build())
      }
    }

    String currentDeletionProtections = currentAttributes.find { it.key() == ATTRIBUTE_DELETION_PROTECTION }?.value()
    String newDeletionProtection = [deletionProtection, deployDefaults.loadBalancing.deletionProtection].findResult(Boolean.FALSE, Closure.IDENTITY).toString()
    if (currentDeletionProtections != newDeletionProtection) {
      task.updateStatus BASE_PHASE, "Setting deletion protection on ${loadBalancerName} to ${newDeletionProtection}."
      attributes.add(LoadBalancerAttribute.builder().key(ATTRIBUTE_DELETION_PROTECTION).value(newDeletionProtection).build())
    }

    // Cross-Zone Load Balancing is only supported in network load balancers
    if (loadBalancer.typeAsString() == 'network' && loadBalancingCrossZone != null) {
      String currentLoadBalancingCrossZone = currentAttributes.find { it.key() == ATTRIBUTE_LOAD_BALANCING_CROSS_ZONE }?.value()
      String newLoadBalancingCrossZone = [loadBalancingCrossZone, deployDefaults.loadBalancing.crossZoneBalancingDefault].findResult(Boolean.TRUE, Closure.IDENTITY).toString()
      if (currentLoadBalancingCrossZone != newLoadBalancingCrossZone) {
        task.updateStatus BASE_PHASE, "Setting Cross-Zone Load Balancing on ${loadBalancerName} to ${newLoadBalancingCrossZone}."
        attributes.add(LoadBalancerAttribute.builder().key(ATTRIBUTE_LOAD_BALANCING_CROSS_ZONE).value(newLoadBalancingCrossZone).build())
      }
    }

    if (!attributes.isEmpty()) {
      loadBalancing.modifyLoadBalancerAttributes(
        ModifyLoadBalancerAttributesRequest.builder()
          .loadBalancerArn(loadBalancerArn)
          .attributes(attributes)
          .build()
      )
    }

    // Get the state of this load balancer from aws
    List<TargetGroup> existingTargetGroups = []
    existingTargetGroups = new ArrayList<>(loadBalancing.describeTargetGroups(
      DescribeTargetGroupsRequest.builder().loadBalancerArn(loadBalancer.loadBalancerArn()).build()
    )?.targetGroups())

    List<Listener> existingListeners = new ArrayList<>(loadBalancing.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build())?.listeners())
    Map<Listener, List<Rule>> existingListenerToRules = existingListeners.collectEntries { listener ->
      List<Rule> rules = loadBalancing.describeRules(DescribeRulesRequest.builder().listenerArn(listener.listenerArn()).build())?.rules()
      [(listener): rules]
    }

    // Can't modify the port or protocol of a target group, so if changed, have to delete/recreate
    List<List<TargetGroup>> targetGroupsSplit = existingTargetGroups.split { awsTargetGroup ->
      (targetGroups.find { it.name == awsTargetGroup.targetGroupName() &&
        it.port == awsTargetGroup.port() &&
        it.protocol.toString() == awsTargetGroup.protocolAsString() }) == null
    }
    List<TargetGroup> targetGroupsToRemove = targetGroupsSplit[0]
    List<TargetGroup> targetGroupsToUpdate = targetGroupsSplit[1]

    List<String> targetGroupArnsToRemove = targetGroupsToRemove.collect { it.targetGroupArn() }
    List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroupsToCreate = targetGroups.findAll { targetGroup ->
      (existingTargetGroups.find { targetGroup.name == it.targetGroupName() &&
        targetGroup.port == it.port() &&
        targetGroup.protocol.toString() == it.protocolAsString() }) == null
    }

    // Find and remove all listeners associated with removed target groups and remove them from existingListeners
    List<Listener> listenersToRemove = existingListeners.findAll { listener ->
      existingListenerToRules.get(listener).any { rule -> rule.actions().any { targetGroupArnsToRemove.contains(it.targetGroupArn()) } }
    }
    removeListeners(listenersToRemove, existingListeners, loadBalancing, loadBalancer)

    // Remove any target groups that we need to remove. This includes target groups that existed previously but were
    // not supplied in the upsert and it also includes target groups that had port, protocol, or ssl policy changed
    List<TargetGroup> removedTargetGroups = removeTargetGroups(targetGroupsToRemove, loadBalancing, loadBalancer, amazonErrors)
    existingTargetGroups.removeAll(removedTargetGroups)

    // Create any target groups to create
    List<TargetGroup> createdTargetGroups = createTargetGroups(targetGroupsToCreate, loadBalancing, loadBalancer, amazonErrors, deployDefaults)
    existingTargetGroups.addAll(createdTargetGroups)

    // Update any target groups that need updating
    updateTargetGroups(targetGroupsToUpdate, targetGroups, loadBalancing, loadBalancer, amazonErrors)

    // Now that we have the union of new target groups and old target groups...
    // Build relationships from listeners to AWS action and rule objects
    Map<UpsertAmazonLoadBalancerV2Description.Listener, List<Action>> listenerToDefaultActions = new HashMap<>()
    Map<UpsertAmazonLoadBalancerV2Description.Listener, List<Rule>> listenerToRules = new HashMap<>()
    listeners.each { listener ->
      List<Action> defaultActions = getAmazonActionsFromDescription(listener.defaultActions, existingTargetGroups, amazonErrors)
      listenerToDefaultActions.put(listener, defaultActions)
      List<Rule> rules = []
      listener.rules.each { rule ->
        List<Action> actions = getAmazonActionsFromDescription(rule.actions, existingTargetGroups, amazonErrors)

        List<RuleCondition> conditions = rule.conditions.collect { condition ->
          if (condition.field == 'http-request-method') {
            HttpRequestMethodConditionConfig httpRequestMethodConditionConfig = HttpRequestMethodConditionConfig.builder().values(condition.values).build()
            RuleCondition.builder().field(condition.field).httpRequestMethodConfig(httpRequestMethodConditionConfig).build()
          } else {
            RuleCondition.builder().field(condition.field).values(condition.values).build()
          }
        }

        rules.add(Rule.builder().actions(actions).conditions(conditions).priority(rule.priority).build())
      }
      listenerToRules.put(listener, rules)
    }

    // Gather list of listeners that existed previously but were not supplied in upsert and should be deleted.
    // also add listeners that have changed since there is no good way to know if a listener should just be updated
    List<List<Listener>> listenersSplit = existingListeners.split { awsListener ->
      listeners.find { it.port == awsListener.port() } == null
    }
    listenersToRemove = listenersSplit[0]
    List<Listener> listenersToUpdate = listenersSplit[1]

    // Create all new listeners
    List<UpsertAmazonLoadBalancerV2Description.Listener> listenersToCreate = listeners.findAll { listener ->
      existingListeners.find { it.port() == listener.port } == null
    }
    listenersToCreate.each { UpsertAmazonLoadBalancerV2Description.Listener listener ->
      createListener(listener, listenerToDefaultActions.get(listener), listenerToRules.get(listener), loadBalancing, loadBalancer, amazonErrors)
    }

    // Update listeners
    listenersToUpdate.each { listener ->
      UpsertAmazonLoadBalancerV2Description.Listener updatedListener = listeners.find {it.port == listener.port() }
      updateListener(listener.listenerArn(),
        updatedListener,
        listenerToDefaultActions.get(updatedListener),
        existingListenerToRules.get(listener).findAll { !it.isDefault },
        listenerToRules.get(updatedListener),
        loadBalancing, amazonErrors)
    }

    if (amazonErrors.size() == 0) {
      removeListeners(listenersToRemove, existingListeners, loadBalancing, loadBalancer)
    }

    if (amazonErrors && amazonErrors.size() > 0) {
      throw new AtomicOperationException("Failed to apply all load balancer updates", amazonErrors)
    }
  }

  static LoadBalancer createLoadBalancer(ElasticLoadBalancingV2Client loadBalancing, String loadBalancerName,
                                         boolean isInternal,
                                         Collection<String> subnetIds, Collection<String> securityGroups,
                                         List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                         List<UpsertAmazonLoadBalancerV2Description.Listener> listeners,
                                         DeployDefaults deployDefaults,
                                         String type,
                                         Integer idleTimeout,
                                         boolean deletionProtection,
                                         boolean loadBalancingCrossZone,
                                         String ipAddressType
  ) {
    def request = CreateLoadBalancerRequest.builder().name(loadBalancerName);

    if (ipAddressType && (type == 'application' || type == 'network')) {
      def addressType = isInternal ? 'ipv4' : ipAddressType
      request.ipAddressType(addressType)
    }

    // Networking Related
    if (subnetIds) {
      task.updateStatus BASE_PHASE, "Subnets: [$subnetIds]"
      request.subnets(subnetIds)
      if (isInternal) {
        request.scheme('internal')
      }
      if (type == 'application') {
        request.securityGroups(securityGroups)
      }
    }

    if (type == 'network') {
      request.type(LoadBalancerTypeEnum.NETWORK)
    } else {
      request.type(LoadBalancerTypeEnum.APPLICATION)
    }

    task.updateStatus BASE_PHASE, "Creating load balancer."
    def result
    try {
      result = loadBalancing.createLoadBalancer(request.build())
    } catch (AwsServiceException e) {
      log.error("Failed to create load balancer", e)
      throw new AtomicOperationException("Failed to create load balancer.", [e.toString()])
    }

    LoadBalancer createdLoadBalancer = null
    List<LoadBalancer> loadBalancers = result.loadBalancers()
    if (loadBalancers != null && loadBalancers.size() > 0) {
      createdLoadBalancer = loadBalancers.get(0)
      updateLoadBalancer(loadBalancing, createdLoadBalancer, securityGroups, targetGroups, listeners, deployDefaults, idleTimeout, deletionProtection, loadBalancingCrossZone, ipAddressType)
    }

    createdLoadBalancer
  }

  private static String handleError(String message, Exception e) {
    log.error(message, e)
    task.updateStatus BASE_PHASE, message
    return message
  }
}
