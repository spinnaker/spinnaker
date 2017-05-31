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
    } finally {
      return null
    }
  }

  public static void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing, LoadBalancer loadBalancer,
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

    // Get existing target groups so we can reconcile
    def existingTargetGroups = []
    if (targetGroups.size() > 0) {
      try {
        existingTargetGroups = loadBalancing.describeTargetGroups(new DescribeTargetGroupsRequest().withNames(targetGroups.collect {
          it.name
        }))?.targetGroups
      } catch (TargetGroupNotFoundException e) {
        // If a subset of the target groups requested does not exist, actually returns the target groups that do exist
        // If none of the target groups requested exist, throws an exception instead.
      }
    }

    // Can't modify the port or protocol of a target group, so if changed, have to delete/recreate
    def targetGroupsSplit = existingTargetGroups.split { awsTargetGroup ->
      (targetGroups.find { it.name == awsTargetGroup.targetGroupName &&
                            it.port == awsTargetGroup.port &&
                            it.protocol.toString() == awsTargetGroup.protocol }) == null
    }
    def targetGroupsToRemove = targetGroupsSplit[0]
    def targetGroupsToUpdate = targetGroupsSplit[1]

    // Remove target groups that existed previously but were not supplied in upsert and should be deleted
    targetGroupsToRemove.each {
      loadBalancing.deleteTargetGroup(new DeleteTargetGroupRequest().withTargetGroupArn(it.targetGroupArn))
      task.updateStatus BASE_PHASE, "Target group removed from ${loadBalancerName} (${it.targetGroupName}:${it.port}:${it.protocol})."
    }

    // Update target groups that already exist
    targetGroupsToUpdate.each { awsTargetGroup ->
      UpsertAmazonLoadBalancerV2Description.TargetGroup targetGroup = targetGroups.find({ it.name == awsTargetGroup.getTargetGroupName() })
      loadBalancing.modifyTargetGroup(new ModifyTargetGroupRequest()
        .withTargetGroupArn(awsTargetGroup.targetGroupArn)
        .withHealthCheckIntervalSeconds(targetGroup.healthCheckInterval)
        .withHealthCheckPath(targetGroup.healthCheckPath)
        .withHealthCheckPort(targetGroup.healthCheckPort)
        .withHealthCheckProtocol(targetGroup.healthCheckProtocol)
        .withHealthCheckTimeoutSeconds(targetGroup.healthCheckTimeout)
        .withUnhealthyThresholdCount(targetGroup.unhealthyThreshold)
        .withMatcher(new Matcher().withHttpCode(targetGroup.healthCheckMatcher))
      )
      task.updateStatus BASE_PHASE, "Target group updated in ${loadBalancerName} (${awsTargetGroup.targetGroupName}:${awsTargetGroup.port}:${awsTargetGroup.protocol})."

      // Update attributes
      String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, awsTargetGroup, targetGroup.attributes)
      if (exceptionMessage) {
        amazonErrors << exceptionMessage
      }
    }

    // Create new target groups
    def targetGroupsToCreate = targetGroups.findAll { targetGroup ->
      (existingTargetGroups.find { targetGroup.name == it.targetGroupName &&
                                    targetGroup.port == it.port &&
                                    targetGroup.protocol.toString() == it.protocol }) == null
    }
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
        def exceptionMessage = "Failed to create target group ${targetGroup.name} for ${loadBalancerName} - reason: ${e.errorMessage}."
        task.updateStatus BASE_PHASE, exceptionMessage
        amazonErrors << exceptionMessage
      }

      if (createdTargetGroup != null) {
        // Add the target group to existing target groups
        existingTargetGroups.add(createdTargetGroup)

        // Add attributes
        String exceptionMessage = modifyTargetGroupAttributes(loadBalancing, createdTargetGroup, targetGroup.attributes)
        if (exceptionMessage) {
          amazonErrors << exceptionMessage
        }
      }
    }

    if (listeners) {
      // Create aws actions for all listeners actions
      Map<UpsertAmazonLoadBalancerV2Description.Listener, List<Action>> listenerToActions = new HashMap<>()
      listeners.each { listener ->
        def actions = []
        listener.defaultActions.each { action ->
          def targetGroup = existingTargetGroups.find { it.targetGroupName == action.targetGroupName }
          if (targetGroup != null) {
            Action awsAction = new Action().withTargetGroupArn(targetGroup.targetGroupArn).withType(action.type)
            actions.add(awsAction)
          } else {
            def exceptionMessage = "Target group name ${action.targetGroupName} not found when trying to create action for listener ${listener.protocol}:${listener.port}"
            task.updateStatus BASE_PHASE, exceptionMessage
            amazonErrors << exceptionMessage
          }
          listenerToActions.put(listener, actions)
        }
      }

      // Get existing listeners so we can reconcile
      def existingListeners = loadBalancing.describeListeners(new DescribeListenersRequest().withLoadBalancerArn(loadBalancerArn))?.listeners

      // Remove listeners that existed previously but were not supplied in upsert and should be deleted;
      // also remove listeners that have changed since there is no good way to know if a listener should just be updated
      def listenersToRemove = existingListeners.findAll { awsListener ->
        (listeners.find { it.compare(awsListener, listenerToActions.get(it)) }) == null
      }
      listenersToRemove.each {
        loadBalancing.deleteListener(new DeleteListenerRequest().withListenerArn(it.listenerArn))
        task.updateStatus BASE_PHASE, "Listener removed from ${loadBalancerName} (${it.port}:${it.protocol})."
      }

      // No unique identifier on listeners to know when to update vs create, so just remove ones that don't match exactly
      def listenersToCreate = listeners.findAll { listener ->
        existingListeners.find({ listener.compare(it, listenerToActions.get(listener)) }) == null
      }

      def createListener = { UpsertAmazonLoadBalancerV2Description.Listener listener, boolean isRollback ->
        try {
          loadBalancing.createListener(new CreateListenerRequest()
            .withLoadBalancerArn(loadBalancerArn)
            .withPort(listener.port)
            .withProtocol(listener.protocol)
            .withSslPolicy(listener.sslPolicy)
            .withDefaultActions(listenerToActions.get(listener)))
          task.updateStatus BASE_PHASE, "Listener ${isRollback ? 'rolled back on' : 'added to'} ${loadBalancerName} (${listener.port}:${listener.protocol})."
        } catch (AmazonServiceException e) {
          def exceptionMessage = "Failed to ${isRollback ? 'roll back' : 'add'} listener to ${loadBalancerName} (${listener.port}:${listener.protocol}) - reason: ${e.errorMessage}."
          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
          return false
        }
        return true
      }

      boolean rollback = false
      listenersToCreate.each { UpsertAmazonLoadBalancerV2Description.Listener listener ->
        if (!createListener(listener, false)) {
          rollback = true
        }
      }

      if (rollback) {
        listenersToRemove.each { Listener awsListener ->
          UpsertAmazonLoadBalancerV2Description.Listener listener = new UpsertAmazonLoadBalancerV2Description.Listener()
          listener.port = awsListener.getPort()
          listener.protocol = ProtocolEnum.fromValue(awsListener.getProtocol())
          listener.sslPolicy = awsListener.getSslPolicy()
          listenerToActions.put(listener, awsListener.getDefaultActions())

          createListener(listener, true)
        }
      }
    }

    if (amazonErrors && amazonErrors.size() > 0) {
      throw new AtomicOperationException("Failed to apply all load balancer updates", amazonErrors)
    }
  }

  public static String createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
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
      errors << e.errorMessage;
      throw new AtomicOperationException("Failed to create load balancer.", errors);
    }

    LoadBalancer createdLoadBalancer = null
    List<LoadBalancer> loadBalancers = result.getLoadBalancers()
    if (loadBalancers != null && loadBalancers.size() > 0) {
      createdLoadBalancer = loadBalancers.get(0)
      updateLoadBalancer(loadBalancing, createdLoadBalancer, securityGroups, targetGroups, listeners);
    }
    createdLoadBalancer
  }
}
