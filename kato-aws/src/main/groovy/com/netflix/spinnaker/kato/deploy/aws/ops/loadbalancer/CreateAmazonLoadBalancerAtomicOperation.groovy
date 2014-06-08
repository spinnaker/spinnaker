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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeSubnetsResult
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestTemplate

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

  @Autowired
  AmazonClientProvider amazonClientProvider

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
      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)

      // Networking Related
      if (description.subnetType) {
        request.withSubnets(getSubnetIds(description.subnetType, amazonEC2))
        if (description.subnetType == "internal") {
          request.scheme = "internal"
        }
      } else {
        request.withAvailabilityZones(availabilityZones)
      }

      // Security Related
      if (description.securityGroups) {
        request.withSecurityGroups(getSecurityGroupIds(amazonEC2, description.securityGroups as String[]))
      }

      // Port mapping
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

      // Go do it
      def client = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region)
      task.updateStatus BASE_PHASE, "Deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}..."
      def result = client.createLoadBalancer(request)
      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}."
      operationResult.loadBalancers[region] = new CreateAmazonLoadBalancerResult.LoadBalancer(loadBalancerName, result.DNSName)
      if (description.healthCheck) {
        def healthCheck = new ConfigureHealthCheckRequest(loadBalancerName, new HealthCheck().withTarget(description.healthCheck).withInterval(10).withTimeout(5).withUnhealthyThreshold(2)
          .withHealthyThreshold(10))
        client.configureHealthCheck(healthCheck)
      }
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
        if (metadata.containsKey("purpose") && metadata.purpose == subnetType.type && metadata.target == SUBNET_PURPOSE_TYPE) {
          mySubnets << subnet
        }
      }
    }
    mySubnets.subnetId
  }

  List<String> getSecurityGroupIds(AmazonEC2 ec2, String... names) {
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
