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
import com.amazonaws.services.elasticloadbalancingv2.model.Action
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerResult
import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateRuleRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteListenerRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DeleteTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.Listener
import com.amazonaws.services.elasticloadbalancingv2.model.ListenerNotFoundException
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.amazonaws.services.elasticloadbalancingv2.model.Matcher
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyTargetGroupAttributesRequest
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyTargetGroupRequest
import com.amazonaws.services.elasticloadbalancingv2.model.ResourceInUseException
import com.amazonaws.services.elasticloadbalancingv2.model.Rule
import com.amazonaws.services.elasticloadbalancingv2.model.RuleCondition
import com.amazonaws.services.elasticloadbalancingv2.model.SetSecurityGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupAttribute
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class LoadBalancerV2UpsertHandler {

  private static final String BASE_PHASE = "UPSERT_ELB_V2"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private static String modifyTargetGroupAttributes(AmazonElasticLoadBalancing loadBalancing, TargetGroup targetGroup, UpsertAmazonLoadBalancerV2Description.Attributes attributes) {
    def targetGroupAttributes = []
    if (attributes) {
      if (attributes.deregistrationDelay) {
        targetGroupAttributes.add(new TargetGroupAttribute(key: "deregistration_delay.timeout_seconds", value: attributes.deregistrationDelay.toString()))
      }
      if (attributes.stickinessEnabled) {
        targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.enabled", value: attributes.stickinessEnabled.toString()))
      }
      if (attributes.stickinessType) {
        targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.type", value: attributes.stickinessType))
      }
      if (attributes.stickinessDuration) {
        targetGroupAttributes.add(new TargetGroupAttribute(key: "stickiness.lb_cookie.duration_seconds", value: attributes.stickinessDuration.toString()))
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

  static List<TargetGroup> createTargetGroups(List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroupsToCreate, AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer, List<String> amazonErrors) {
    String loadBalancerName = loadBalancer.loadBalancerName
    List<TargetGroup> createdTargetGroups = new ArrayList<TargetGroup>()

    targetGroupsToCreate.each { targetGroup ->
      TargetGroup createdTargetGroup
      try {
        CreateTargetGroupResult createTargetGroupResult = loadBalancing.createTargetGroup(new CreateTargetGroupRequest()
          .withProtocol(targetGroup.protocol)
          .withPort(targetGroup.port)
          .withName(targetGroup.name)
          .withVpcId(loadBalancer.vpcId)
          .withHealthCheckIntervalSeconds(targetGroup.healthCheckInterval)
          .withHealthCheckPath(targetGroup.healthCheckPath)
          .withHealthCheckPort(targetGroup.healthCheckPort)
          .withHealthCheckProtocol(targetGroup.healthCheckProtocol)
          .withHealthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
          .withHealthyThresholdCount(targetGroup.healthyThreshold)
          .withUnhealthyThresholdCount(targetGroup.unhealthyThreshold)
          .withMatcher(new Matcher().withHttpCode(targetGroup.healthCheckMatcher))
        )
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
        String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, createdTargetGroup, targetGroup.attributes)
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
      loadBalancing.modifyTargetGroup(new ModifyTargetGroupRequest()
        .withTargetGroupArn(awsTargetGroup.targetGroupArn)
        .withHealthCheckIntervalSeconds(targetGroup.healthCheckInterval)
        .withHealthCheckPath(targetGroup.healthCheckPath)
        .withHealthCheckPort(targetGroup.healthCheckPort)
        .withHealthCheckProtocol(targetGroup.healthCheckProtocol)
        .withHealthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
        .withHealthyThresholdCount(targetGroup.healthyThreshold)
        .withMatcher(new Matcher().withHttpCode(targetGroup.healthCheckMatcher))
        .withUnhealthyThresholdCount(targetGroup.unhealthyThreshold)
      )
      task.updateStatus BASE_PHASE, "Target group updated in ${loadBalancer.loadBalancerName} (${awsTargetGroup.targetGroupName}:${awsTargetGroup.port}:${awsTargetGroup.protocol})."

      // Update attributes
      String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, awsTargetGroup, targetGroup.attributes)
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

  static void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer,
                                        Collection<String> securityGroups,
                                        List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                        List<UpsertAmazonLoadBalancerV2Description.Listener> listeners) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName
    def loadBalancerArn = loadBalancer.loadBalancerArn
    if (loadBalancer.vpcId && !securityGroups) {
      throw new IllegalArgumentException("Load balancer ${loadBalancerName} must have at least one security group")
    }

    if (securityGroups) {
      loadBalancing.setSecurityGroups(new SetSecurityGroupsRequest(
        loadBalancerArn: loadBalancerArn,
        securityGroups: securityGroups
      ))
    }

    task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."

    // Get the state of this load balancer from aws
    List<TargetGroup> existingTargetGroups = []
    if (targetGroups.size() > 0) {
      try {
        existingTargetGroups = loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest().withNames(targetGroups.collect { it.name }))?.targetGroups
      } catch (TargetGroupNotFoundException ignore) {
        // If a subset of the target groups requested does not exist, actually returns the target groups that do exist
        // If none of the target groups requested exist, throws an exception instead.
      }
    }


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
    List<TargetGroup> createdTargetGroups = createTargetGroups(targetGroupsToCreate, loadBalancing, loadBalancer, amazonErrors)
    existingTargetGroups.addAll(createdTargetGroups)

    // Update any target groups that need updating
    updateTargetGroups(targetGroupsToUpdate, targetGroups, loadBalancing, loadBalancer, amazonErrors)

    // Now that we have the union of new target groups and old target groups...
    // Build relationships from listeners to AWS action and rule objects
    Map<UpsertAmazonLoadBalancerV2Description.Listener, List<Action>> listenerToDefaultActions = new HashMap<>()
    Map<UpsertAmazonLoadBalancerV2Description.Listener, List<Rule>> listenerToRules = new HashMap<>()
    listeners.each { listener ->
      List<Action> defaultActions = []
      listener.defaultActions.each { action ->
        TargetGroup targetGroup = existingTargetGroups.find { it.targetGroupName == action.targetGroupName }
        if (targetGroup != null) {
          Action awsAction = new Action().withTargetGroupArn(targetGroup.targetGroupArn).withType(action.type)
          defaultActions.add(awsAction)
        } else {
          String exceptionMessage = "Target group name ${action.targetGroupName} not found when trying to create action for listener ${listener.protocol}:${listener.port}"
          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
        }
        listenerToDefaultActions.put(listener, defaultActions)
      }
      List<Rule> rules = []
      listener.rules.each { rule ->
        List<Action> actions = []
        rule.actions.each { action ->
          TargetGroup targetGroup = existingTargetGroups.find { it.targetGroupName == action.targetGroupName }
          if (targetGroup != null) {
            actions.add(new Action().withTargetGroupArn(targetGroup.targetGroupArn).withType(action.type))
          }
        }

        List<RuleCondition> conditions = rule.conditions.collect { condition ->
          new RuleCondition().withField(condition.field).withValues(condition.values)
        }

        rules.add(new Rule().withActions(actions).withConditions(conditions).withPriority(Integer.toString(rule.priority)))
      }
      listenerToRules.put(listener, rules)
    }

    // Create all new listeners
    List<UpsertAmazonLoadBalancerV2Description.Listener> listenersToCreate = listeners.findAll { listener ->
      existingListeners.find({ listener.compare(it, listenerToDefaultActions.get(listener), existingListenerToRules.get(it), listenerToRules.get(listener)) }) == null
    }
    listenersToCreate.each { UpsertAmazonLoadBalancerV2Description.Listener listener ->
      createListener(listener, listenerToDefaultActions.get(listener), listenerToRules.get(listener), loadBalancing, loadBalancer, amazonErrors)
    }

    if (amazonErrors.size() == 0) {
      // Remove listeners that existed previously but were not supplied in upsert and should be deleted;
      // also remove listeners that have changed since there is no good way to know if a listener should just be updated
      listenersToRemove = existingListeners.findAll { awsListener ->
        (listeners.find { it.compare(awsListener, listenerToDefaultActions.get(it), existingListenerToRules.get(awsListener), listenerToRules.get(it)) }) == null
      }
      removeListeners(listenersToRemove, existingListeners, loadBalancing, loadBalancer)
    }

    if (amazonErrors && amazonErrors.size() > 0) {
      throw new AtomicOperationException("Failed to apply all load balancer updates", amazonErrors)
    }
  }

  static LoadBalancer createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
                                          Collection<String> subnetIds, Collection<String> securityGroups,
                                          List<UpsertAmazonLoadBalancerV2Description.TargetGroup> targetGroups,
                                          List<UpsertAmazonLoadBalancerV2Description.Listener> listeners) {
    def request = new CreateLoadBalancerRequest().withName(loadBalancerName)

    // Networking Related
    if (subnetIds) {
      task.updateStatus BASE_PHASE, "Subnets: [$subnetIds]"
      request.withSubnets(subnetIds)
      if (isInternal) {
        request.scheme = 'internal'
      }
      request.withSecurityGroups(securityGroups)
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
      updateLoadBalancer(loadBalancing, createdLoadBalancer, securityGroups, targetGroups, listeners)
    }
    createdLoadBalancer
  }
}
