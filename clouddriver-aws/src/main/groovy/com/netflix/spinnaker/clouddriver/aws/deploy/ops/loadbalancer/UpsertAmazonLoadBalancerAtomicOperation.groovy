/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import org.springframework.beans.factory.annotation.Autowired
/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerDescription}.
 *
 *
 */
class UpsertAmazonLoadBalancerAtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerResult> {
  private static final String BASE_PHASE = "CREATE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  private final UpsertAmazonLoadBalancerDescription description
  ObjectMapper objectMapper = new ObjectMapper()

  UpsertAmazonLoadBalancerAtomicOperation(UpsertAmazonLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  UpsertAmazonLoadBalancerResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new UpsertAmazonLoadBalancerResult(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def loadBalancerName = description.name ?: "${description.clusterName}-frontend".toString()

      //maintains bwc with the contains internal check.
      boolean isInternal = description.isInternal != null ? description.isInternal : description.subnetType?.contains('internal')

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region, true)

      LoadBalancerDescription loadBalancer

      task.updateStatus BASE_PHASE, "Setting up listeners for ${loadBalancerName} in ${region}..."
      def listeners = []
      for (UpsertAmazonLoadBalancerDescription.Listener listener : description.listeners) {
        def awsListener = new Listener()
        awsListener.withLoadBalancerPort(listener.externalPort).withInstancePort(listener.internalPort)

        awsListener.withProtocol(listener.externalProtocol.name())
        if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
          awsListener.withInstanceProtocol(listener.internalProtocol.name())
        } else {
          awsListener.withInstanceProtocol(listener.externalProtocol.name())
        }
        if (listener.sslCertificateId) {
          task.updateStatus BASE_PHASE, "Attaching listener with SSL Certificate: ${listener.sslCertificateId}"
          awsListener.withSSLCertificateId(listener.sslCertificateId)
        }
        listeners << awsListener
        task.updateStatus BASE_PHASE, "Appending listener ${awsListener.protocol}:${awsListener.loadBalancerPort} -> ${awsListener.instanceProtocol}:${awsListener.instancePort}"
      }

      try {
        loadBalancer = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest([loadBalancerName]))?.
                loadBalancerDescriptions?.getAt(0)
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
      } catch (AmazonServiceException ignore) {
      }

      def securityGroups = regionScopedProvider.securityGroupService.
              getSecurityGroupIds(description.securityGroups, description.vpcId).values()

      String dnsName
      if (!loadBalancer) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB)
        }
        dnsName = createLoadBalancer(loadBalancing, loadBalancerName, isInternal, availabilityZones, subnetIds, listeners, securityGroups)
      } else {
        dnsName = loadBalancer.DNSName
        updateLoadBalancer(loadBalancing, loadBalancer, listeners, securityGroups)
      }

      // Configure health checks
      if (description.healthCheck) {
        task.updateStatus BASE_PHASE, "Configuring healthcheck for ${loadBalancerName} in ${region}..."
        def healthCheck = new ConfigureHealthCheckRequest(loadBalancerName, new HealthCheck()
                .withTarget(description.healthCheck).withInterval(description.healthInterval)
                .withTimeout(description.healthTimeout).withUnhealthyThreshold(description.unhealthyThreshold)
                .withHealthyThreshold(description.healthyThreshold))
        loadBalancing.configureHealthCheck(healthCheck)
        task.updateStatus BASE_PHASE, "Healthcheck configured."
      }

      // Apply balancing opinions...
      loadBalancing.modifyLoadBalancerAttributes(
        new ModifyLoadBalancerAttributesRequest(loadBalancerName: loadBalancerName)
          .withLoadBalancerAttributes(
          new LoadBalancerAttributes(
            crossZoneLoadBalancing: new CrossZoneLoadBalancing(enabled: description.crossZoneBalancing)
          )
        )
      )

      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new UpsertAmazonLoadBalancerResult.LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  private void updateLoadBalancer(AmazonElasticLoadBalancing loadBalancing, LoadBalancerDescription loadBalancer,
                                  List<Listener> listeners, Collection<String> securityGroups) {
    def amazonErrors = []
    def loadBalancerName = loadBalancer.loadBalancerName

    if (listeners) {
      def existingListeners = loadBalancer.listenerDescriptions*.listener
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

      listeners.each { Listener listener ->
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

    if (description.securityGroups) {
      loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest(
              loadBalancerName: loadBalancerName,
              securityGroups: securityGroups
      ))
      task.updateStatus BASE_PHASE, "Security groups updated on ${loadBalancerName}."
    }

    if (amazonErrors) {
      throw new AtomicOperationException("Failed to apply all load balancer updates", amazonErrors)
    }
  }

  private String createLoadBalancer(AmazonElasticLoadBalancing loadBalancing, String loadBalancerName, boolean isInternal,
                                    Collection<String> availabilityZones, Collection<String> subnetIds,
                                    Collection<Listener> listeners, Collection<String> securityGroups) {
    def request = new CreateLoadBalancerRequest(loadBalancerName)

    // Networking Related
    if (description.subnetType) {
      task.updateStatus BASE_PHASE, "Subnet type: ${description.subnetType} = [$subnetIds]"
      request.withSubnets(subnetIds)
      if (isInternal) {
        request.scheme = 'internal'
      }
    } else {
      request.withAvailabilityZones(availabilityZones)
    }
    task.updateStatus BASE_PHASE, "Creating load balancer."
    request.withListeners(listeners)
    request.withSecurityGroups(securityGroups)
    def result = loadBalancing.createLoadBalancer(request)
    result.DNSName
  }

}
