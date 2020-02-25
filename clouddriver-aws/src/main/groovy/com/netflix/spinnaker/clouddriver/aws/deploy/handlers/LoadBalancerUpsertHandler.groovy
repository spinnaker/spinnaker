/*
 * Copyright 2016 Netflix, Inc.
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
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.ListenerDescription
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.amazonaws.services.elasticloadbalancing.model.SetLoadBalancerPoliciesOfListenerRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import groovy.util.logging.Slf4j

@Slf4j
class LoadBalancerUpsertHandler {

  private static final String BASE_PHASE = "UPSERT_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  public static void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing, LoadBalancerDescription loadBalancer,
                                  List<Listener> listeners, Collection<String> securityGroups) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName
    if (loadBalancer.getVPCId() && !securityGroups) {
      throw new IllegalArgumentException("Load balancer ${loadBalancerName} must have at least one security group")
    }

    if (securityGroups) {
      loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
        loadBalancerName: loadBalancerName,
        securityGroups: securityGroups
      ))
    }

    task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."

    if (listeners) {
      // ignore all references to :0 => :0 listeners - leave them alone if they're there, do not add them if they're not
      listeners = listeners.findAll(notLegacyListener);
      def existingListeners = loadBalancer.listenerDescriptions*.listener.findAll(notLegacyListener)
      def listenersToRemove = existingListeners.findAll {
        // existed previously but were not supplied in upsert and should be deleted
        !listeners.contains(it)
      }
      listeners.removeAll(listenersToRemove)

      // no need to recreate existing listeners
      listeners.removeAll(existingListeners)
      final List<ListenerDescription> listenerDescriptionsToRemove = loadBalancer
        .listenerDescriptions
        .findAll {
          it.listener in listenersToRemove
        }

      def createListener = { ListenerDescription listenerDescription, boolean isRollback ->
        try {
          loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(loadBalancerName, [listenerDescription.listener]))
          if (!listenerDescription.policyNames.isEmpty()) {
            ensureSetLoadBalancerListenerPolicies(loadBalancerName, listenerDescription, loadBalancing)
          }

          task.updateStatus BASE_PHASE,
            "Listener ${isRollback ? 'rolled back on' : 'added to'} ${loadBalancerName} " +
              "(${listenerDescription.listener.loadBalancerPort}:${listenerDescription.listener.protocol}:${listenerDescription.listener.instancePort})."
        } catch (AmazonServiceException e) {
          def exceptionMessage = "Failed to ${isRollback ? 'roll back' : 'add'} listener to ${loadBalancerName} " +
            "(${listenerDescription.listener.loadBalancerPort}:${listenerDescription.listener.protocol}:${listenerDescription.listener.instancePort}) " +
            "- reason: ${e.errorMessage}."

          task.updateStatus BASE_PHASE, exceptionMessage
          amazonErrors << exceptionMessage
          return false
        }
        return true
      }

      boolean rollback = false
      listenerDescriptionsToRemove.each {
        try {
          loadBalancing.deleteLoadBalancerListeners(
            new DeleteLoadBalancerListenersRequest(loadBalancerName, [it.listener.loadBalancerPort])
          )

          task.updateStatus BASE_PHASE,
            "Listener removed from ${loadBalancerName} (${it.listener.loadBalancerPort}:${it.listener.protocol}:${it.listener.instancePort})."
        } catch(AmazonServiceException e) {
          // Rollback as this failure will result in an exception when creating listeners.
          task.updateStatus BASE_PHASE, "Failed to remove listener $it: $e.errorMessage."
          amazonErrors << e.errorMessage
        }
      }

      listeners.each { listener ->
        final List<String> policyNames = loadBalancer
          .listenerDescriptions.find {
            it.listener.loadBalancerPort == listener.loadBalancerPort && it.listener.protocol == listener.protocol
          }?.policyNames

        final ListenerDescription description = new ListenerDescription(listener: listener, policyNames: policyNames)
        if (!createListener(description, false)) {
          rollback = true
        }
      }

      if (amazonErrors || rollback) {
        listenerDescriptionsToRemove.each {
          createListener(it, true)
        }
      }
    }

    if (amazonErrors) {
      throw new AtomicOperationException("Failed to apply all load balancer updates", amazonErrors)
    }
  }

  public static String createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
                                          Collection<String> availabilityZones, Collection<String> subnetIds,
                                          Collection<Listener> listeners, Collection<String> securityGroups) {
    return createLoadBalancer(loadBalancing, loadBalancerName, isInternal, availabilityZones, subnetIds, listeners, securityGroups, null)
  }

  public static String createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
                                          Collection<String> availabilityZones, Collection<String> subnetIds,
                                          Collection<Listener> listeners, Collection<String> securityGroups, LoadBalancerAttributes sourceAttributes) {
    def request = new CreateLoadBalancerRequest(loadBalancerName)

    // Networking Related
    if (subnetIds) {
      task.updateStatus BASE_PHASE, "Subnets: [$subnetIds]"
      request.withSubnets(subnetIds)
      if (isInternal) {
        request.scheme = 'internal'
      }
      request.withSecurityGroups(securityGroups)
    } else {
      request.withAvailabilityZones(availabilityZones)
    }
    request.withListeners(listeners)
    task.updateStatus BASE_PHASE, "Creating load balancer."
    def result = loadBalancing.createLoadBalancer(request)
    if (sourceAttributes) {
      task.updateStatus BASE_PHASE, "Configuring load balancer attributes."
      loadBalancing.modifyLoadBalancerAttributes(
        new ModifyLoadBalancerAttributesRequest()
          .withLoadBalancerAttributes(sourceAttributes)
          .withLoadBalancerName(loadBalancerName)
      )
    }
    result.DNSName
  }

  // ignore the old listener :0 => :0, which AWS adds to ELBs created sometime before 2012-09-26
  private static Closure notLegacyListener = { Listener listener ->
    listener.instancePort != 0 && listener.loadBalancerPort != 0 && listener.protocol
  }

  /**
   * Ensures policies set in the request are applied to the load balancer
   */
  private static void ensureSetLoadBalancerListenerPolicies(
    String loadBalancerName, ListenerDescription listenerDescription, AmazonElasticLoadBalancing loadBalancing) {
    final SetLoadBalancerPoliciesOfListenerRequest policyRequest = new SetLoadBalancerPoliciesOfListenerRequest()
      .withLoadBalancerName(loadBalancerName)
      .withLoadBalancerPort(listenerDescription.listener.loadBalancerPort)

    try {
      loadBalancing.setLoadBalancerPoliciesOfListener(
        policyRequest.withPolicyNames(listenerDescription.policyNames)
      )
    } catch(AmazonServiceException e) {
      log.error("Failed to set listener policies on loadbalancer $loadBalancerName: $e.errorMessage")
    }
  }
}
