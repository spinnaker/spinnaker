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

import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient
import software.amazon.awssdk.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.Listener
import software.amazon.awssdk.services.elasticloadbalancing.model.ListenerDescription
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerAttributes
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription
import software.amazon.awssdk.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import software.amazon.awssdk.services.elasticloadbalancing.model.SetLoadBalancerPoliciesOfListenerRequest
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

  public static void updateLoadBalancer(ElasticLoadBalancingClient loadBalancing, LoadBalancerDescription loadBalancer,
                                  List<Listener> listeners, Collection<String> securityGroups) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName()
    if (loadBalancer.vpcId() && !securityGroups) {
      throw new IllegalArgumentException("Load balancer ${loadBalancerName} must have at least one security group")
    }

    if (securityGroups) {
      loadBalancing.applySecurityGroupsToLoadBalancer(ApplySecurityGroupsToLoadBalancerRequest.builder()
        .loadBalancerName(loadBalancerName)
        .securityGroups(securityGroups)
        .build())
    }

    task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."

    if (listeners) {
      // ignore all references to :0 => :0 listeners - leave them alone if they're there, do not add them if they're not
      listeners = listeners.findAll(notLegacyListener);
      def existingListeners = loadBalancer.listenerDescriptions()*.listener().findAll(notLegacyListener)
      def listenersToRemove = existingListeners.findAll {
        // existed previously but were not supplied in upsert and should be deleted
        !listeners.contains(it)
      }
      listeners.removeAll(listenersToRemove)

      // no need to recreate existing listeners
      listeners.removeAll(existingListeners)
      final List<ListenerDescription> listenerDescriptionsToRemove = loadBalancer
        .listenerDescriptions()
        .findAll {
          it.listener() in listenersToRemove
        }

      def createListener = { ListenerDescription listenerDescription, boolean isRollback ->
        try {
          loadBalancing.createLoadBalancerListeners(CreateLoadBalancerListenersRequest.builder()
            .loadBalancerName(loadBalancerName)
            .listeners([listenerDescription.listener()])
            .build())
          if (!listenerDescription.policyNames().isEmpty()) {
            ensureSetLoadBalancerListenerPolicies(loadBalancerName, listenerDescription, loadBalancing)
          }

          task.updateStatus BASE_PHASE,
            "Listener ${isRollback ? 'rolled back on' : 'added to'} ${loadBalancerName} " +
              "(${listenerDescription.listener().loadBalancerPort()}:${listenerDescription.listener().protocol()}:${listenerDescription.listener().instancePort()})."
        } catch (AwsServiceException e) {
          def exceptionMessage = "Failed to ${isRollback ? 'roll back' : 'add'} listener to ${loadBalancerName} " +
            "(${listenerDescription.listener().loadBalancerPort()}:${listenerDescription.listener().protocol()}:${listenerDescription.listener().instancePort()}) " +
            "- reason: ${e.awsErrorDetails().errorMessage()}."

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
            DeleteLoadBalancerListenersRequest.builder()
              .loadBalancerName(loadBalancerName)
              .loadBalancerPorts([it.listener().loadBalancerPort()])
              .build()
          )

          task.updateStatus BASE_PHASE,
            "Listener removed from ${loadBalancerName} (${it.listener().loadBalancerPort()}:${it.listener().protocol()}:${it.listener().instancePort()})."
        } catch(AwsServiceException e) {
          // Rollback as this failure will result in an exception when creating listeners.
          task.updateStatus BASE_PHASE, "Failed to remove listener $it: ${e.awsErrorDetails().errorMessage()}."
          amazonErrors << e.awsErrorDetails().errorMessage()
        }
      }

      listeners.each { listener ->
        final List<String> policyNames = loadBalancer
          .listenerDescriptions().find {
            it.listener().loadBalancerPort() == listener.loadBalancerPort() && it.listener().protocol() == listener.protocol()
          }?.policyNames()

        final ListenerDescription description = ListenerDescription.builder().listener(listener).policyNames(policyNames).build()
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

  public static String createLoadBalancer(ElasticLoadBalancingClient loadBalancing, String loadBalancerName, boolean isInternal,
                                          Collection<String> availabilityZones, Collection<String> subnetIds,
                                          Collection<Listener> listeners, Collection<String> securityGroups) {
    return createLoadBalancer(loadBalancing, loadBalancerName, isInternal, availabilityZones, subnetIds, listeners, securityGroups, null)
  }

  public static String createLoadBalancer(ElasticLoadBalancingClient loadBalancing, String loadBalancerName, boolean isInternal,
                                          Collection<String> availabilityZones, Collection<String> subnetIds,
                                          Collection<Listener> listeners, Collection<String> securityGroups, LoadBalancerAttributes sourceAttributes) {
    def requestBuilder = CreateLoadBalancerRequest.builder().loadBalancerName(loadBalancerName)

    // Networking Related
    if (subnetIds) {
      task.updateStatus BASE_PHASE, "Subnets: [$subnetIds]"
      requestBuilder.subnets(subnetIds)
      if (isInternal) {
        requestBuilder.scheme('internal')
      }
      requestBuilder.securityGroups(securityGroups)
    } else {
      requestBuilder.availabilityZones(availabilityZones)
    }
    requestBuilder.listeners(listeners)
    task.updateStatus BASE_PHASE, "Creating load balancer."
    def result = loadBalancing.createLoadBalancer(requestBuilder.build())
    if (sourceAttributes) {
      task.updateStatus BASE_PHASE, "Configuring load balancer attributes."
      loadBalancing.modifyLoadBalancerAttributes(
        ModifyLoadBalancerAttributesRequest.builder()
          .loadBalancerAttributes(sourceAttributes)
          .loadBalancerName(loadBalancerName)
          .build()
      )
    }
    result.dnsName()
  }

  // ignore the old listener :0 => :0, which AWS adds to ELBs created sometime before 2012-09-26
  private static Closure notLegacyListener = { Listener listener ->
    listener.instancePort() != 0 && listener.loadBalancerPort() != 0 && listener.protocol()
  }

  /**
   * Ensures policies set in the request are applied to the load balancer
   */
  private static void ensureSetLoadBalancerListenerPolicies(
    String loadBalancerName, ListenerDescription listenerDescription, ElasticLoadBalancingClient loadBalancing) {
    final SetLoadBalancerPoliciesOfListenerRequest policyRequest = SetLoadBalancerPoliciesOfListenerRequest.builder()
      .loadBalancerName(loadBalancerName)
      .loadBalancerPort(listenerDescription.listener().loadBalancerPort())
      .policyNames(listenerDescription.policyNames())
      .build()

    try {
      loadBalancing.setLoadBalancerPoliciesOfListener(policyRequest)
    } catch(AwsServiceException e) {
      log.error("Failed to set listener policies on loadbalancer $loadBalancerName: ${e.awsErrorDetails().errorMessage()}")
    }
  }
}
