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

package com.netflix.asgard.kato.deploy.aws.ops.loadbalancer

import com.amazonaws.services.ec2.model.SubnetState
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.asgard.kato.data.task.Task
import com.netflix.asgard.kato.data.task.TaskRepository
import com.netflix.asgard.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.asgard.kato.orchestration.AtomicOperation
import org.springframework.web.client.RestTemplate

import static com.netflix.asgard.kato.deploy.aws.StaticAmazonClients.getAmazonElasticLoadBalancing

/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link CreateAmazonLoadBalancerDescription}.
 *
 * @author Dan Woods
 */
class CreateAmazonLoadBalancerAtomicOperation implements AtomicOperation<CreateAmazonLoadBalancerResult> {
  private static final String SUBNET_METADATA_KEY = "immutable_metadata"
  private static final String SUBNET_PURPOSE_TYPE = "elb"
  private static final String BASE_PHASE = "CREATE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateAmazonLoadBalancerDescription description
  RestTemplate rt = new RestTemplate()
  ObjectMapper objectMapper = new ObjectMapper()

  CreateAmazonLoadBalancerAtomicOperation(CreateAmazonLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  CreateAmazonLoadBalancerResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new CreateAmazonLoadBalancerResult(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def loadBalancerName = "${description.clusterName}-frontend"

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def request = new CreateLoadBalancerRequest(loadBalancerName)

      if (description.subnetType) {
        request.withSubnets(getSubnetIds(description.subnetType, region, description.credentials.environment))
        if (description.subnetType == "internal") {
          request.scheme = "internal"
        }
      } else {
        request.withAvailabilityZones(availabilityZones)
      }

      if (description.securityGroups) {
        request.withSecurityGroups(getSecurityGroupIds(region, description.credentials.environment, description.securityGroups as String[]))
      }

      def listeners = []
      for (CreateAmazonLoadBalancerDescription.Listener listener : description.listeners) {
        def awsListener = new Listener()
        awsListener.withLoadBalancerPort(listener.externalPort).withInstancePort(listener.internalPort)

        awsListener.withProtocol(listener.externalProtocol.name())
        if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
          awsListener.withInstanceProtocol(listener.internalProtocol.name())
        } else {
          awsListener.withInstanceProtocol(listener.externalProtocol.name())
        }
        listeners << awsListener
        task.updateStatus BASE_PHASE, " > Appending listener ${awsListener.protocol}:${awsListener.loadBalancerPort} -> ${awsListener.instanceProtocol}:${awsListener.instancePort}"
      }
      request.withListeners(listeners)

      def client = getAmazonElasticLoadBalancing(description.credentials, region)
      task.updateStatus BASE_PHASE, "Deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}..."
      def result = client.createLoadBalancer(request)
      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}."
      operationResult.loadBalancers[region] = new CreateAmazonLoadBalancerResult.LoadBalancer(loadBalancerName, result.DNSName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  List<String> getSubnetIds(String subnetType, String region, String environment) {
    List<Map> subnets = rt.getForObject("http://entrypoints-v2.${region}.${environment}.netflix.net:7001/REST/v2/aws/subnets;_expand", List)
    def mySubnets = []
    for (subnet in subnets) {
      def metadataJson = subnet.tags.find { it.key == SUBNET_METADATA_KEY }?.value
      if (metadataJson) {
        Map metadata = objectMapper.readValue metadataJson, Map
        if (metadata.containsKey("purpose") && metadata.purpose == subnetType && metadata.target == SUBNET_PURPOSE_TYPE && subnet.state == SubnetState.Available.toString()) {
          mySubnets << subnet.subnetId
        }
      }
    }
    mySubnets
  }

  List<String> getSecurityGroupIds(String region, String environment, String...names) {
    List<Map> securityGroups = rt.getForObject("http://entrypoints-v2.${region}.${environment}.netflix.net:7001/REST/v2/aws/securityGroups;_expand", List)
    def mySecurityGroups = [:]
    for (secGrp in securityGroups) {
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
