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


package com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.elasticloadbalancing.model.ApplySecurityGroupsToLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerListenersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerDescription}.
 *
 * @author Dan Woods
 */
class UpsertAmazonLoadBalancerAtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerResult> {
  private static final String SUBNET_METADATA_KEY = "immutable_metadata"
  private static final String SUBNET_PURPOSE_TYPE = "elb"
  private static final String BASE_PHASE = "CREATE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

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
      def loadBalancerName = description.name ?: "${description.clusterName}-frontend".toString()

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region)
      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)

      LoadBalancerDescription loadBalancer

      def getLoadBalancer = {
        loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest([loadBalancerName]))?.loadBalancerDescriptions?.getAt(0)
      }

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
          task.updateStatus BASE_PHASE, " > Attaching listener with SSL Certificate: ${listener.sslCertificateId}"
          awsListener.withSSLCertificateId(listener.sslCertificateId)
        }
        listeners << awsListener
        task.updateStatus BASE_PHASE, " > Appending listener ${awsListener.protocol}:${awsListener.loadBalancerPort} -> ${awsListener.instanceProtocol}:${awsListener.instancePort}"
      }

      try {
        loadBalancer = getLoadBalancer()
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
      } catch (AmazonServiceException ignore) {}

      if (!loadBalancer) {
        task.updateStatus BASE_PHASE, "Deploying ${loadBalancerName} to ${description.credentials.name} in ${region}..."
        def request = new CreateLoadBalancerRequest(loadBalancerName)

        // Networking Related
        if (description.subnetType) {
          def subnets = getSubnetIds(description.subnetType, availabilityZones, amazonEC2)
          task.updateStatus BASE_PHASE, " > Subnet type: ${description.subnetType} = [$subnets]"
          request.withSubnets(subnets)
          if (description.subnetType == "internal") {
            request.scheme = description.subnetType
          }
        } else {
          request.withAvailabilityZones(availabilityZones)
        }
        task.updateStatus BASE_PHASE, " > Creating load balancer."
        request.withListeners(listeners)
        loadBalancing.createLoadBalancer(request)
        loadBalancer = getLoadBalancer()
      } else {
        // Apply listeners...
        def ports = loadBalancer.listenerDescriptions.collect { it.listener.loadBalancerPort }
        loadBalancing.deleteLoadBalancerListeners(new DeleteLoadBalancerListenersRequest(loadBalancerName, ports))
        loadBalancing.createLoadBalancerListeners(new CreateLoadBalancerListenersRequest(loadBalancerName, listeners))
        task.updateStatus BASE_PHASE, "New listeners applied for ${loadBalancerName} in ${region}!"
      }

      // Apply security groups...
      if (description.securityGroups) {
        task.updateStatus BASE_PHASE, "Applying security groups ${description.securityGroups} to ${loadBalancerName} in ${region}"
        def securityGroups = getSecurityGroupIds(amazonEC2, description.securityGroups as String[])
        loadBalancing.applySecurityGroupsToLoadBalancer(new ApplySecurityGroupsToLoadBalancerRequest().withSecurityGroups(securityGroups).withLoadBalancerName(loadBalancerName))
        task.updateStatus BASE_PHASE, " > Security groups applied!"
      }

      // Configure health checks
      if (description.healthCheck) {
        task.updateStatus BASE_PHASE, "Configuring healthcheck for ${loadBalancerName} in ${region}..."
        def healthCheck = new ConfigureHealthCheckRequest(loadBalancerName, new HealthCheck()
          .withTarget(description.healthCheck).withInterval(description.healthInterval)
          .withTimeout(description.healthTimeout).withUnhealthyThreshold(description.unhealthyThreshold)
          .withHealthyThreshold(description.healthyThreshold))
        loadBalancing.configureHealthCheck(healthCheck)
        task.updateStatus BASE_PHASE, "Healthcheck configured!"
      }
      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new UpsertAmazonLoadBalancerResult.LoadBalancer(loadBalancerName, loadBalancer.DNSName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  List<String> getSubnetIds(String subnetType, List<String> availabilityZones, AmazonEC2 amazonEC2) {
    DescribeSubnetsResult result = amazonEC2.describeSubnets()
    List<Subnet> mySubnets = []
    for (subnet in result.subnets) {
      if (availabilityZones && !availabilityZones.contains(subnet.availabilityZone)) {
        continue
      }
      def metadataJson = subnet.tags.find { it.key == SUBNET_METADATA_KEY }?.value
      if (metadataJson) {
        Map metadata = objectMapper.readValue metadataJson, Map
        if (metadata.containsKey("purpose") && metadata.purpose == subnetType && metadata.target == SUBNET_PURPOSE_TYPE) {
          mySubnets << subnet
        }
      }
    }
    mySubnets.subnetId
  }

  static List<String> getSecurityGroupIds(AmazonEC2 ec2, String... names) {
    def result = ec2.describeSecurityGroups()
    def mySecurityGroups = [:]
    for (secGrp in result.securityGroups) {
      if (names.contains(secGrp.groupName)) {
        mySecurityGroups[secGrp.groupName] = secGrp.groupId
      }
    }
    if (names.minus(mySecurityGroups.keySet()).size() > 0) {
      null
    } else {
      mySecurityGroups.values() as List
    }
  }
}
