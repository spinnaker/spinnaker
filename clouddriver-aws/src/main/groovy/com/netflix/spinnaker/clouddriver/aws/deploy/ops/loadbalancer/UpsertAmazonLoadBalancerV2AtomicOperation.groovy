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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.amazonaws.services.shield.AWSShield
import com.amazonaws.services.shield.model.CreateProtectionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerV2UpsertHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

/**
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerV2Description}.
 *
 *
 */
@Slf4j
class UpsertAmazonLoadBalancerV2AtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerV2Result> {
  private static final String BASE_PHASE = "CREATE_ELB_V2"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  SecurityGroupLookupFactory securityGroupLookupFactory

  @Autowired
  IngressLoadBalancerBuilder ingressLoadBalancerBuilder

  @Autowired
  DeployDefaults deployDefaults

  private final UpsertAmazonLoadBalancerV2Description description
  ObjectMapper objectMapper = new ObjectMapper()

  UpsertAmazonLoadBalancerV2AtomicOperation(UpsertAmazonLoadBalancerDescription description) {
    this.description = (UpsertAmazonLoadBalancerV2Description) description
  }

  @Override
  UpsertAmazonLoadBalancerV2Result operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new UpsertAmazonLoadBalancerV2Result(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def loadBalancerName = description.name ?: "${description.clusterName}-frontend".toString()

      //maintains bwc with the contains internal check.
      boolean isInternal = description.getIsInternal() != null ? description.getIsInternal() : description.subnetType?.contains('internal')

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.credentials, region, true)

      // Set up security groups
      def securityGroups = regionScopedProvider.securityGroupService.
              getSecurityGroupIds(description.securityGroups, description.vpcId).collect { it.value }

      // Check if load balancer already exists
      LoadBalancer loadBalancer
      try {
        DescribeLoadBalancersResult result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest(names: [loadBalancerName] ))
        loadBalancer = result.loadBalancers.size() > 0 ? result.loadBalancers.get(0) : null
      } catch (AmazonServiceException ignore) {
      }

      // Create/Update load balancer
      String dnsName
      if (loadBalancer == null) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} of type ${description.loadBalancerType} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB, 1)
        }
        handleSecurityGroupIngress(region, securityGroups)
        loadBalancer = LoadBalancerV2UpsertHandler.createLoadBalancer(loadBalancing, loadBalancerName, isInternal, subnetIds, securityGroups, description.targetGroups, description.listeners, deployDefaults, description.loadBalancerType.toString(), description.idleTimeout, description.deletionProtection)
        dnsName = loadBalancer.DNSName

        // Enable AWS shield. We only do this on creation. The ELB must be external, the account must be enabled with
        // AWS Shield Protection and the description must not opt out of protection.
        if (!description.isInternal && description.credentials.shieldEnabled && description.shieldProtectionEnabled) {
          task.updateStatus BASE_PHASE, "Configuring AWS Shield for ${loadBalancerName} in ${region}..."
          try {
            AWSShield shieldClient = amazonClientProvider.getAmazonShield(description.credentials, region)
            shieldClient.createProtection(
              new CreateProtectionRequest()
                .withName(loadBalancerName)
                .withResourceArn(loadBalancer.getLoadBalancerArn())
            )
            task.updateStatus BASE_PHASE, "AWS Shield configured for ${loadBalancerName} in ${region}."
          } catch (Exception e) {
            log.error("Failed to enable AWS Shield protection on $loadBalancerName", e)
            task.updateStatus BASE_PHASE, "Failed to configure AWS Shield for ${loadBalancerName} in ${region}."
          }
        }
      } else {
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
        dnsName = loadBalancer.DNSName
        LoadBalancerV2UpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, securityGroups, description.targetGroups, description.listeners, deployDefaults, description.idleTimeout, description.deletionProtection)
      }

      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new UpsertAmazonLoadBalancerV2Result.LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  private void handleSecurityGroupIngress(String region, Collection<String> securityGroups) {
    // require that we have addAppGroupToServerGroup as well as createLoadBalancerIngressPermissions
    // set since the load balancer ingress assumes that application group is the target of those
    // permissions
    if (deployDefaults.createLoadBalancerIngressPermissions && deployDefaults.addAppGroupToServerGroup) {
      String application = null
      try {
        application = Names.parseName(description.name).getApp() ?: Names.parseName(description.clusterName).getApp()
        Set<Integer> ports = []
        description.targetGroups.each { tg ->
          ports.add(tg.port)
          if (tg.healthCheckPort && tg.healthCheckPort != "traffic-port") {
            ports.add(Integer.parseInt(tg.healthCheckPort, 10))
          }
        }
        IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult ingressLoadBalancerResult = ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(
          application,
          region,
          description.credentialAccount,
          description.credentials,
          description.vpcId,
          ports,
          securityGroupLookupFactory
        )
        if (!securityGroups.any { it == ingressLoadBalancerResult.groupId }) {
          securityGroups.add(ingressLoadBalancerResult.groupId)
        }

        task.updateStatus BASE_PHASE, "Authorized app ELB Security Group ${ingressLoadBalancerResult}"
      } catch (Exception e) {
        log.error("Failed to authorize app LB security group {}-elb on application security group", application, e)
        task.updateStatus BASE_PHASE, "Failed to authorize app ELB security group ${application}-elb on application security group"
      }
    }
  }
}
