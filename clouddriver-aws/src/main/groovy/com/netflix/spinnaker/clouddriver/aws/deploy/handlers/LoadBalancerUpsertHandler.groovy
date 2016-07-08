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
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException

class LoadBalancerUpsertHandler {

  private static final String BASE_PHASE = "UPSERT_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  public static void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing, LoadBalancerDescription loadBalancer,
                                  List<Listener> listeners, Collection<String> securityGroups) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName

    if (securityGroups) {
      loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
        loadBalancerName: loadBalancerName,
        securityGroups: securityGroups
      ))
      task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."
    }

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

      listenersToRemove.each {
        loadBalancing.deleteLoadBalancerListeners(
          new DeleteLoadBalancerListenersRequest(loadBalancerName, [it.loadBalancerPort])
        )
        task.updateStatus BASE_PHASE, "Listener removed from ${loadBalancerName} (${it.loadBalancerPort}:${it.protocol}:${it.instancePort})."
      }

      listeners
        .each { Listener listener ->
          try {
            loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(loadBalancerName, [listener]))
            task.updateStatus BASE_PHASE, "Listener added to ${loadBalancerName} (${listener.loadBalancerPort}:${listener.protocol}:${listener.instancePort})."
          } catch (AmazonServiceException e) {
            def exceptionMessage = "Failed to add listener to ${loadBalancerName} (${listener.loadBalancerPort}:${listener.protocol}:${listener.instancePort}) - reason: ${e.errorMessage}."
            task.updateStatus BASE_PHASE, exceptionMessage
            amazonErrors << exceptionMessage
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
    result.DNSName
  }

  // ignore the old listener :0 => :0, which AWS adds to ELBs created sometime before 2012-09-26
  private static Closure notLegacyListener = { Listener listener ->
    listener.instancePort != 0 && listener.loadBalancerPort != 0 && listener.protocol
  }

}
