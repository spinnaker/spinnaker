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

import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.elasticloadbalancing.model.*
import software.amazon.awssdk.services.shield.ShieldClient
import software.amazon.awssdk.services.shield.model.CreateProtectionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerUpsertHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult.LoadBalancer
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
 * An AtomicOperation for creating an Elastic Load Balancer from the description of {@link UpsertAmazonLoadBalancerClassicDescription}.
 *
 *
 */
@Slf4j
class UpsertAmazonLoadBalancerAtomicOperation implements AtomicOperation<UpsertAmazonLoadBalancerResult> {
  private static final String BASE_PHASE = "CREATE_ELB"

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

  private final UpsertAmazonLoadBalancerClassicDescription description
  ObjectMapper objectMapper = new ObjectMapper()

  UpsertAmazonLoadBalancerAtomicOperation(UpsertAmazonLoadBalancerDescription description) {
    this.description = (UpsertAmazonLoadBalancerClassicDescription) description
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
      boolean isInternal = description.getIsInternal() != null ? description.getIsInternal() : description.subnetType?.contains('internal')

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingClassicV2(description.credentials, region)

      LoadBalancerDescription loadBalancer = null

      task.updateStatus BASE_PHASE, "Setting up listeners for ${loadBalancerName} in ${region}..."
      List<Listener> listeners = []
      description.listeners
        .each { UpsertAmazonLoadBalancerClassicDescription.Listener listener ->
          def awsListenerBuilder = Listener.builder()
          awsListenerBuilder.loadBalancerPort(listener.externalPort).instancePort(listener.internalPort)

          awsListenerBuilder.protocol(listener.externalProtocol.name())
          if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
            awsListenerBuilder.instanceProtocol(listener.internalProtocol.name())
          } else {
            awsListenerBuilder.instanceProtocol(listener.externalProtocol.name())
          }
          if (listener.sslCertificateId) {
            task.updateStatus BASE_PHASE, "Attaching listener with SSL ServerCertificate: ${listener.sslCertificateId}"
            awsListenerBuilder.sslCertificateId(listener.sslCertificateId)
          }
          def awsListener = awsListenerBuilder.build()
          listeners << awsListener
          task.updateStatus BASE_PHASE, "Appending listener ${awsListener.protocol()}:${awsListener.loadBalancerPort()} -> ${awsListener.instanceProtocol()}:${awsListener.instancePort()}"
        }

      try {
        loadBalancer = loadBalancing.describeLoadBalancers(DescribeLoadBalancersRequest.builder().loadBalancerNames([loadBalancerName]).build())?.
                loadBalancerDescriptions()?.getAt(0)
        task.updateStatus BASE_PHASE, "Found existing load balancer named ${loadBalancerName} in ${region}... Using that."
      } catch (AwsServiceException ignore) {
      }

      Set<String> securityGroups = regionScopedProvider.securityGroupService.getSecurityGroupIds(description.securityGroups, description.vpcId)
        .collect { it.value }
      log.info("security groups on {} {}", description.name, securityGroups)
      String dnsName
      if (!loadBalancer) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB, 1)
        }

        // require that we have addAppGroupToServerGroup as well as createLoadBalancerIngressPermissions
        // set since the load balancer ingress assumes that application group is the target of those
        // permissions
        if (deployDefaults.createLoadBalancerIngressPermissions && deployDefaults.addAppGroupToServerGroup) {
          String application = null
          try {
            application = Names.parseName(description.name).getApp() ?: Names.parseName(description.clusterName).getApp()
            Set<Integer> ports = listeners.collect { l -> l.instancePort() }
            if (description.healthCheckPort) {
              ports.add(description.healthCheckPort)
            }
            IngressLoadBalancerBuilder.IngressLoadBalancerGroupResult ingressLoadBalancerResult = ingressLoadBalancerBuilder.ingressApplicationLoadBalancerGroup(
              application,
              region,
              description.account,
              description.credentials,
              description.vpcId,
              ports,
              securityGroupLookupFactory
            )

            task.updateStatus BASE_PHASE, "Authorized app ELB Security Group ${ingressLoadBalancerResult}"
            securityGroups.add(ingressLoadBalancerResult.groupId)
          } catch (Exception e) {
            log.error("Failed to authorize app ELB security group {}-elb on application security group", application,  e)
            task.updateStatus BASE_PHASE, "Failed to authorize app ELB security group ${application}-elb on application security group"
          }
        }

        dnsName = LoadBalancerUpsertHandler.createLoadBalancer(loadBalancing, loadBalancerName, isInternal, availabilityZones, subnetIds, listeners, securityGroups)

        // Enable AWS shield. We only do this on creation. The ELB must be external, the account must be enabled with
        // AWS Shield Protection and the description must not opt out of protection.
        if (!description.isInternal && description.credentials.shieldEnabled && description.shieldProtectionEnabled) {
          task.updateStatus BASE_PHASE, "Configuring AWS Shield for ${loadBalancerName} in ${region}..."
          try {
            ShieldClient shieldClient = amazonClientProvider.getAmazonShieldV2(description.credentials, region)
            shieldClient.createProtection(
              CreateProtectionRequest.builder()
                .name(loadBalancerName)
                .resourceArn(loadBalancerArn(description.credentials.accountId, region, loadBalancerName))
                .build()
            )
            task.updateStatus BASE_PHASE, "AWS Shield configured for ${loadBalancerName} in ${region}."
          } catch (Exception e) {
            log.error("Failed to enable AWS Shield protection on $loadBalancerName", e)
            task.updateStatus BASE_PHASE, "Failed to configure AWS Shield for ${loadBalancerName} in ${region}."
          }
        }
      } else {
        dnsName = loadBalancer.dnsName()
        LoadBalancerUpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, listeners, securityGroups)
      }

      // Configure health checks
      if (description.healthCheck) {
        task.updateStatus BASE_PHASE, "Configuring healthcheck for ${loadBalancerName} in ${region}..."
        def healthCheck = ConfigureHealthCheckRequest.builder().loadBalancerName(loadBalancerName).healthCheck(HealthCheck.builder()
                .target(description.healthCheck).interval(description.healthInterval)
                .timeout(description.healthTimeout).unhealthyThreshold(description.unhealthyThreshold)
                .healthyThreshold(description.healthyThreshold).build()).build()
        loadBalancing.configureHealthCheck(healthCheck)
        task.updateStatus BASE_PHASE, "Healthcheck configured."
      }

      CrossZoneLoadBalancing crossZoneLoadBalancing = null
      ConnectionDraining connectionDraining = null
      ConnectionSettings connectionSettings = null

      if (loadBalancer) {
        def currentAttributes = loadBalancing.describeLoadBalancerAttributes(DescribeLoadBalancerAttributesRequest.builder().loadBalancerName(loadBalancerName).build()).loadBalancerAttributes()

        Boolean crossZoneBalancingEnabled = [description.crossZoneBalancing, currentAttributes?.crossZoneLoadBalancing()?.enabled(), deployDefaults.loadBalancing.crossZoneBalancingDefault].findResult(Closure.IDENTITY)

        if (crossZoneBalancingEnabled != currentAttributes?.crossZoneLoadBalancing()?.enabled()) {
          crossZoneLoadBalancing = CrossZoneLoadBalancing.builder().enabled(crossZoneBalancingEnabled).build()
        }

        Boolean connectionDrainingEnabled = [description.connectionDraining, currentAttributes?.connectionDraining()?.enabled(), deployDefaults.loadBalancing.connectionDrainingDefault].findResult(Closure.IDENTITY)
        Integer deregistrationDelay = [description.deregistrationDelay, currentAttributes?.connectionDraining()?.timeout(), deployDefaults.loadBalancing.deregistrationDelayDefault].findResult(Closure.IDENTITY)

        if (connectionDrainingEnabled != currentAttributes?.connectionDraining()?.enabled() || deregistrationDelay != currentAttributes?.connectionDraining()?.timeout()) {
          connectionDraining = ConnectionDraining.builder()
            .enabled(connectionDrainingEnabled)
            .timeout(deregistrationDelay).build()
        }

        Integer idleTimeout = [description.idleTimeout, currentAttributes?.connectionSettings()?.idleTimeout(), deployDefaults.loadBalancing.idleTimeout].findResult(Closure.IDENTITY)
        if (idleTimeout != currentAttributes?.connectionSettings()?.idleTimeout()) {
          connectionSettings = ConnectionSettings.builder().idleTimeout(idleTimeout).build()
        }

      } else {
        crossZoneLoadBalancing = CrossZoneLoadBalancing.builder().enabled([description.crossZoneBalancing, deployDefaults.loadBalancing.crossZoneBalancingDefault].findResult(Boolean.TRUE, Closure.IDENTITY)).build()
        connectionDraining = ConnectionDraining.builder()
          .enabled([description.connectionDraining, deployDefaults.loadBalancing.connectionDrainingDefault].findResult(Boolean.FALSE, Closure.IDENTITY))
          .timeout([description.deregistrationDelay, deployDefaults.loadBalancing.deregistrationDelayDefault].findResult(Closure.IDENTITY)).build()
        connectionSettings = ConnectionSettings.builder()
          .idleTimeout([description.idleTimeout, deployDefaults.loadBalancing.idleTimeout].findResult(Closure.IDENTITY)).build()
      }

      if (crossZoneLoadBalancing != null || connectionDraining != null || connectionSettings != null) {
        def attributesBuilder = LoadBalancerAttributes.builder()
        if (crossZoneLoadBalancing) {
          attributesBuilder.crossZoneLoadBalancing(crossZoneLoadBalancing)
        }
        if (connectionDraining) {
          attributesBuilder.connectionDraining(connectionDraining)
        }
        if (connectionSettings) {
          attributesBuilder.connectionSettings(connectionSettings)
        }
        // Apply balancing opinions...
        loadBalancing.modifyLoadBalancerAttributes(
          ModifyLoadBalancerAttributesRequest.builder()
            .loadBalancerName(loadBalancerName)
            .loadBalancerAttributes(attributesBuilder.build())
            .build()
          )
      }


      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  private static String loadBalancerArn(String accountId, String region, String name) {
    return "arn:aws:elasticloadbalancing:$accountId:$region:loadbalancer/$name"
  }
}
