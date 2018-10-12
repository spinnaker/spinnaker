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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.*
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults

class LoadBalancerV2UpsertHandler {

  private static final String BASE_PHASE = "UPSERT_ELB_V2"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private static String modifyTargetGroupAttributes(AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, TargetGroup targetGroup, UpsertAmazonLoadBalancerV2Description.Attributes attributes) {
    return modifyTargetGroupAttributes(loadBalancing, loadBalancer, targetGroup, attributes, null)
  }
  private static String modifyTargetGroupAttributes(AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, TargetGroup targetGroup, UpsertAmazonLoadBalancerV2Description.Attributes attributes, DeployDefaults deployDefaults) {
    def targetGroupAttributes = []
    if (attributes) {
      Integer deregistrationDelay = [attributes.deregistrationDelay, deployDefaults?.loadBalancing?.deregistrationDelayDefault].findResult(Closure.IDENTITY)
      if (deregistrationDelay != null) {
        targetGroupAttributes.add(new TargetGroupAttribute(key: "deregistration_delay.timeout_seconds", value: deregistrationDelay.toString()))
      }
      if (loadBalancer.type == 'application') {
        if (attributes.stickinessEnabled != null) {
          targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.enabled", value: attributes.stickinessEnabled.toString()))
        }
        if (attributes.stickinessType != null) {
          targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.type", value: attributes.stickinessType))
        }
        if (attributes.stickinessDuration != null) {
          targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.lb_cookie.duration_seconds", value: attributes.stickinessDuration.toString()))
        }
      }
      if(loadBalancer.type == 'network' ){
        if(attributes.proxyProtocolV2 != null){
          targetGroupAttributes.add(new TargetGroupAttribute(key: "proxy_protocol_v2.enabled", value: attributes.proxyProtocolV2))
        }
      }
    }

    try {
      loadBalancing.modifyTargetGroupAttributes(new ModifyTargetGroupAttributesRequest()
        .withTargetGroupArn(targetGroup.targetGroupArn)
        .withAttributes(targetGroupAttributes))
      task.updateStatus BASE_PHASE, "Modified target group ${targetGroup.targetGroupName} attributes."
    } catch (AmazonServiceException e) {
      def exceptionMessage = "Failed to modify attributes for target group ${targetGroup.targetGroupName} - reason: ${e.errorMessage}."
      task.updateStatus BASE_PHASE, exceptionMessage
      return exceptionMessage
    }
    return null
  }

  static List<TargetGroup> createTargetGroups(List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroupsToCreate, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors, DeployDefaults deployDefaults) {
    String loadBalancerName = loadBalancer.loadBalancerName
    List<TargetGroup> createdTargetGroups = new ArrayList<TargetGroup>()

    targetGroupsToCreate.each { targetGroup ->
      TargetGroup createdTargetGroup
      try {

        CreateTargetGroupRequest createTargetGroupRequest = new CreateTargetGroupRequest()
          .withProtocol(targetGroup.protocol)
          .withPort(targetGroup.port)
          .withName(targetGroup.name)
          .withVpcId(loadBalancer.vpcId)
          .withHealthCheckIntervalSeconds(targetGroup.healthCheckInterval)
          .withHealthCheckPort(targetGroup.healthCheckPort)
          .withHealthCheckProtocol(targetGroup.healthCheckProtocol)
          .withHealthyThresholdCount(targetGroup.healthyThreshold)
          .withUnhealthyThresholdCount(targetGroup.unhealthyThreshold)
          .withTargetType(targetGroup.targetType)

        if (targetGroup.healthCheckProtocol in [ProtocolEnum.HTTP, ProtocolEnum.HTTPS]) {
          createTargetGroupRequest
            .withHealthCheckPath(targetGroup.healthCheckPath)

          // HTTP(s) health checks for TCP does not support custom matchers and timeouts. Also, health thresholds must be equal.
          if (targetGroup.protocol == ProtocolEnum.TCP) {
            createTargetGroupRequest.withUnhealthyThresholdCount(createTargetGroupRequest.getHealthyThresholdCount())
          } else {
            createTargetGroupRequest.withMatcher(new Matcher().withHttpCode(targetGroup.healthCheckMatcher))
              .withHealthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
          }
        }

        CreateTargetGroupResult createTargetGroupResult = loadBalancing.createTargetGroup( createTargetGroupRequest )
        task.updateStatus BASE_PHASE, "Target group created in ${loadBalancerName} (${targetGroup.name}:${targetGroup.port}:${targetGroup.protocol})."
        createdTargetGroup = createTargetGroupResult.getTargetGroups().get(0)
      } catch (AmazonServiceException e) {
        String exceptionMessage = "Failed to create target group ${targetGroup.name} for ${loadBalancerName} - reason: ${e.errorMessage}."
        task.updateStatus BASE_PHASE, exceptionMessage
        amazonErrors << exceptionMessage
      }

      if (createdTargetGroup != null) {
        // Add the target group to existing target groups
        createdTargetGroups.add(createdTargetGroup)

        // Add attributes
        String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, loadBalancer, createdTargetGroup, targetGroup.attributes, deployDefaults)
        if (exceptionMessage) {
          amazonErrors << exceptionMessage
        }
      }
    }

    return createdTargetGroups
  }

  static List<TargetGroup> removeTargetGroups(List<TargetGroup> targetGroupsToRemove, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    List<TargetGroup> removedTargetGroups = new ArrayList<>()
    targetGroupsToRemove.each {
      try {
        loadBalancing.deleteTargetGroup(new DeleteTargetGroupRequest().withTargetGroupArn(it.targetGroupArn))
        removedTargetGroups.push(it)
        task.updateStatus BASE_PHASE, "Target group removed from ${loadBalancer.loadBalancerName} (${it.targetGroupName}:${it.port}:${it.protocol})."
      } catch (ResourceInUseException e) {
        String exceptionMessage = "Failed to delete target group ${it.targetGroupName} from ${loadBalancer.loadBalancerName} - reason: ${e.errorMessage}."
        task.updateStatus BASE_PHASE, exceptionMessage
        amazonErrors << exceptionMessage
      }
    }
    return removedTargetGroups
  }

  static void updateTargetGroups(List<TargetGroup> targetGroupsToUpdate, List<UpsertAmazonLoadBalancerV2Description.TargetGroup> updatedTargetGroups, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    targetGroupsToUpdate.each { awsTargetGroup ->
      UpsertAmazonLoadBalancerV2Description.TargetGroup targetGroup = updatedTargetGroups.find({ it.name == awsTargetGroup.getTargetGroupName() })

      ModifyTargetGroupRequest modifyTargetGroupRequest = new ModifyTargetGroupRequest()
        .withTargetGroupArn(awsTargetGroup.targetGroupArn)
        .withHealthCheckIntervalSeconds(targetGroup.healthCheckInterval)
        .withHealthCheckPort(targetGroup.healthCheckPort)
        .withHealthCheckProtocol(targetGroup.healthCheckProtocol)
        .withHealthyThresholdCount(targetGroup.healthyThreshold)
        .withUnhealthyThresholdCount(targetGroup.unhealthyThreshold)

      if (targetGroup.healthCheckProtocol in [ProtocolEnum.HTTP, ProtocolEnum.HTTPS]) {
        modifyTargetGroupRequest
          .withHealthCheckPath(targetGroup.healthCheckPath)

        // HTTP(s) health checks for TCP does not support custom matchers and timeouts. Also, health thresholds must be equal.
        if (targetGroup.protocol == ProtocolEnum.TCP) {
          modifyTargetGroupRequest.withUnhealthyThresholdCount(modifyTargetGroupRequest.getHealthyThresholdCount())
        } else {
          modifyTargetGroupRequest.withMatcher(new Matcher().withHttpCode(targetGroup.healthCheckMatcher))
            .withHealthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
        }
      }

      loadBalancing.modifyTargetGroup(modifyTargetGroupRequest)
      task.updateStatus BASE_PHASE, "Target group updated in ${loadBalancer.loadBalancerName} (${awsTargetGroup.targetGroupName}:${awsTargetGroup.port}:${awsTargetGroup.protocol})."

      // Update attributes
      String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, loadBalancer, awsTargetGroup, targetGroup.attributes)
      if (exceptionMessage) {
        amazonErrors << exceptionMessage
      }
    }
  }

  static boolean createListener(UpsertAmazonLoadBalancerV2Description.Listener listener, List<Action> defaultActions, List<Rule> rules, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    CreateListenerResult result
    try {
      result = loadBalancing.createListener(new CreateListenerRequest()
        .withLoadBalancerArn(loadBalancer.loadBalancerArn)
        .withPort(listener.port)
        .withProtocol(listener.protocol)
        .withCertificates(listener.certificates)
        .withSslPolicy(listener.sslPolicy)
        .withDefaultActions(defaultActions))
      task.updateStatus BASE_PHASE, "Listener added to ${loadBalancer.loadBalancerName} (${listener.port}:${listener.protocol})."
    } catch (AmazonServiceException e) {
      String exceptionMessage = "Failed to add listener to ${loadBalancer.loadBalancerName} (${listener.port}:${listener.protocol}) - reason: ${e.errorMessage}."
      task.updateStatus BASE_PHASE, exceptionMessage
      amazonErrors << exceptionMessage
      return false
    }

    if (result != null && result.listeners.size() > 0) {
      String listenerArn = result.listeners.get(0).listenerArn
      try {
        rules.each { rule ->
          loadBalancing.createRule(new CreateRuleRequest(listenerArn: listenerArn, conditions: rule.conditions, actions: rule.actions, priority: Integer.valueOf(rule.priority)))
        }
      } catch (AmazonServiceException e) {
        String exceptionMessage = "Failed to add rule to listener ${loadBalancer.loadBalancerName} (${listener.port}:${listener.protocol}) reason: ${e.errorMessage}."
        task.updateStatus BASE_PHASE, exceptionMessage
        amazonErrors << exceptionMessage
        return false
      }
    }

    return true
  }

  static boolean containsAllRules(List<Rule> aRules, List<Rule> bRules) {
    !aRules.any { aRule ->
      boolean foundMatchingRule = bRules.any { bRule ->
        bRule.actions.containsAll(aRule.actions) && aRule.actions.containsAll(bRule.actions) &&
          bRule.conditions.containsAll(aRule.conditions) && aRule.conditions.containsAll(bRule.conditions) &&
          bRule.priority == aRule.priority
      }
      return !foundMatchingRule
    }
  }

  static void updateListener(String listenerArn,
                             UpsertAmazonLoadBalancerV2Description.Listener listener,
                             List<Action> defaultActions, List<Rule> existingRules,
                             List<Rule> newRules,
                             AmazonElasticLoadBalancing loadBalancing,
                             List<String> amazonErrors) {
    try {
      loadBalancing.modifyListener(new ModifyListenerRequest()
        .withListenerArn(listenerArn)
        .withProtocol(listener.protocol)
        .withCertificates(listener.certificates)
        .withSslPolicy(listener.sslPolicy)
        .withDefaultActions(defaultActions))
      task.updateStatus BASE_PHASE, "Listener ${listenerArn} updated (${listener.port}:${listener.protocol})."
    } catch (AmazonServiceException e) {
      String exceptionMessage = "Failed to modify listener ${listenerArn} (${listener.port}:${listener.protocol}) - reason: ${e.errorMessage}."
      task.updateStatus BASE_PHASE, exceptionMessage
      amazonErrors << exceptionMessage
    }

    // Compare the old rules; if any are different, just replace them all.
    boolean rulesSame = existingRules.size() == newRules.size() &&
      containsAllRules(existingRules, newRules) &&
      containsAllRules(newRules, existingRules)

    if (!rulesSame) {
      existingRules.each { rule ->
        try {
          loadBalancing.deleteRule(new DeleteRuleRequest(ruleArn: rule.ruleArn))
        } catch (AmazonServiceException ignore) {
          // If the rule failed to be deleted, it could not be found, so we should be safe to create the new ones.
        }
      }
      newRules.each { rule ->
        try {
          loadBalancing.createRule(new CreateRuleRequest(listenerArn: listenerArn, conditions: rule.conditions, actions: rule.actions, priority: Integer.valueOf(rule.priority)))
        } catch (AmazonServiceException e) {
          String exceptionMessage = "Failed to add rule to listener ${listenerArn} (${listener.port}:${listener.protocol}) reason: ${e.errorMessage}."
          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
        }
      }
    }
  }

  static void removeListeners(List<Listener> listenersToRemove, List<Listener> existingListeners, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer) {
    listenersToRemove.each {
      try {
        loadBalancing.deleteListener(new DeleteListenerRequest().withListenerArn(it.listenerArn))
        task.updateStatus BASE_PHASE, "Listener removed from ${loadBalancer.loadBalancerName} (${it.port}:${it.protocol})."
        existingListeners.remove(it)
      } catch (ListenerNotFoundException e) {
        task.updateStatus BASE_PHASE, "Failed to delete listener ${it.listenerArn}. Listener could not be found. ${e.errorMessage}"
      }
    }
  }

  static List<Action> getAmazonActionsFromDescription(List<UpsertAmazonLoadBalancerV2Description.Action> actions, List<TargetGroup> existingTargetGroups, List<String> amazonErrors) {
    List<Action> awsActions = []
    actions.eachWithIndex { action, index ->
      if (action.type == "forward") {
        TargetGroup targetGroup = existingTargetGroups.find { it.targetGroupName == action.targetGroupName }
        if (targetGroup != null) {
          Action awsAction = new Action().withType(action.type).withTargetGroupArn(targetGroup.targetGroupArn).withOrder(index + 1)
          awsActions.add(awsAction)
        } else {
          String exceptionMessage = "Target group name ${action.targetGroupName} not found when trying to create action"
          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
        }
      } else if (action.type == "authenticate-oidc") {
        Action awsAction = new Action().withType(action.type).withAuthenticateOidcConfig(action.authenticateOidcActionConfig).withOrder(index + 1)
        awsActions.add(awsAction)
      }
    }
    awsActions
  }

  static void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing,
                                 LoadBalancer loadBalancer,
                                 Collection<String> securityGroups,
                                 List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                 List<UpsertAmazonLoadBalancerV2Description.Listener> listeners,
                                 DeployDefaults deployDefaults) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName
    def loadBalancerArn = loadBalancer.loadBalancerArn

    if (loadBalancer.getType() == 'application') {
      if (loadBalancer.vpcId && !securityGroups) {
        throw new IllegalArgumentException("Load balancer ${loadBalancerName} must have at least one security group")
      }

      if (securityGroups) {
        loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
          loadBalancerArn: loadBalancerArn,
          securityGroups: securityGroups
        ))
        task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."
      }
    }

    // Get the state of this load balancer from aws
    List<TargetGroup> existingTargetGroups = []
    existingTargetGroups = loadBalancing.describeTargetGroups(
      new DescribeTargetGroupsRequest().withLoadBalancerArn(loadBalancer.loadBalancerArn)
    )?.targetGroups

    List<Listener> existingListeners = loadBalancing.describeListeners(new DescribeListenersRequest().withLoadBalancerArn(loadBalancerArn))?.listeners
    Map<Listener, List<Rule>> existingListenerToRules = existingListeners.collectEntries { listener ->
      List<Rule> rules = loadBalancing.describeRules(new DescribeRulesRequest(listenerArn: listener.listenerArn))?.rules
      [(listener): rules]
    }

    // Can't modify the port or protocol of a target group, so if changed, have to delete/recreate
    List<List<TargetGroup>> targetGroupsSplit = existingTargetGroups.split { awsTargetGroup ->
      (targetGroups.find { it.name == awsTargetGroup.targetGroupName &&
                            it.port == awsTargetGroup.port &&
                            it.protocol.toString() == awsTargetGroup.protocol }) == null
    }
    List<TargetGroup> targetGroupsToRemove = targetGroupsSplit[0]
    List<TargetGroup> targetGroupsToUpdate = targetGroupsSplit[1]

    List<String> targetGroupArnsToRemove = targetGroupsToRemove.collect { it.targetGroupArn }
    List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroupsToCreate = targetGroups.findAll { targetGroup ->
      (existingTargetGroups.find { targetGroup.name == it.targetGroupName &&
        targetGroup.port == it.port &&
        targetGroup.protocol.toString() == it.protocol }) == null
    }

    // Find and remove all listeners associated with removed target groups and remove them from existingListeners
    List<Listener> listenersToRemove = existingListeners.findAll { listener ->
      existingListenerToRules.get(listener).any { rule -> rule.actions.any { targetGroupArnsToRemove.contains(it.targetGroupArn) } }
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
          new RuleCondition().withField(condition.field).withValues(condition.values)
        }

        rules.add(new Rule().withActions(actions).withConditions(conditions).withPriority(Integer.toString(rule.priority)))
      }
      listenerToRules.put(listener, rules)
    }

    // Gather list of listeners that existed previously but were not supplied in upsert and should be deleted.
    // also add listeners that have changed since there is no good way to know if a listener should just be updated
    List<List<Listener>> listenersSplit = existingListeners.split { awsListener ->
      listeners.find { it.port == awsListener.port } == null
    }
    listenersToRemove = listenersSplit[0]
    List<Listener> listenersToUpdate = listenersSplit[1]

    // Create all new listeners
    List<UpsertAmazonLoadBalancerV2Description.Listener> listenersToCreate = listeners.findAll { listener ->
      existingListeners.find { it.port == listener.port } == null
    }
    listenersToCreate.each { UpsertAmazonLoadBalancerV2Description.Listener listener ->
      createListener(listener, listenerToDefaultActions.get(listener), listenerToRules.get(listener), loadBalancing, loadBalancer, amazonErrors)
    }

    // Update listeners
    listenersToUpdate.each { listener ->
      UpsertAmazonLoadBalancerV2Description.Listener updatedListener = listeners.find {it.port == listener.port }
      updateListener(listener.listenerArn,
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

  static LoadBalancer createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
                                         Collection<String> subnetIds, Collection<String> securityGroups,
                                         List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                         List<UpsertAmazonLoadBalancerV2Description.Listener> listeners,
                                         DeployDefaults deployDefaults,
                                         String type) {
    def request = new CreateLoadBalancerRequest().withName(loadBalancerName)

    // Networking Related
    if (subnetIds) {
      task.updateStatus BASE_PHASE, "Subnets: [$subnetIds]"
      request.withSubnets(subnetIds)
      if (isInternal) {
        request.scheme = 'internal'
      }
      if (type == 'application') {
        request.withSecurityGroups(securityGroups)
      }
    }

    if (type == 'network') {
      request.setType(LoadBalancerTypeEnum.Network)
    } else {
      request.setType(LoadBalancerTypeEnum.Application)
    }

    task.updateStatus BASE_PHASE, "Creating load balancer."
    def result
    try {
      result = loadBalancing.createLoadBalancer(request)
    } catch (AmazonServiceException e) {
      def errors = []
      errors << e.errorMessage
      throw new AtomicOperationException("Failed to create load balancer.", errors)
    }

    LoadBalancer createdLoadBalancer = null
    List<LoadBalancer> loadBalancers = result.getLoadBalancers()
    if (loadBalancers != null && loadBalancers.size() > 0) {
      createdLoadBalancer = loadBalancers.get(0)
      updateLoadBalancer(loadBalancing, createdLoadBalancer, securityGroups, targetGroups, listeners, deployDefaults)
    }
    createdLoadBalancer
  }
}
