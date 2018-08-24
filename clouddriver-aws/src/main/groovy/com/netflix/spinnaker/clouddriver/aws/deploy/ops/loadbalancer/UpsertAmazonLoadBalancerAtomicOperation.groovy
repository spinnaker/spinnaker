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
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.SecurityGroup
import com.amazonaws.services.ec2.model.UserIdGroupPair
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest
import com.amazonaws.services.elasticloadbalancing.model.ConnectionDraining
import com.amazonaws.services.elasticloadbalancing.model.CrossZoneLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancerAttributesRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerAttributes
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.amazonaws.services.elasticloadbalancing.model.ModifyLoadBalancerAttributesRequest
import com.amazonaws.services.shield.AWSShield
import com.amazonaws.services.shield.model.CreateProtectionRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.LoadBalancerUpsertHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult.LoadBalancer
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup
import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater
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

      def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(description.credentials, region, true)

      LoadBalancerDescription loadBalancer = null

      task.updateStatus BASE_PHASE, "Setting up listeners for ${loadBalancerName} in ${region}..."
      def listeners = []
      description.listeners
        .each { UpsertAmazonLoadBalancerClassicDescription.Listener listener ->
          def awsListener = new Listener()
          awsListener.withLoadBalancerPort(listener.externalPort).withInstancePort(listener.internalPort)

          awsListener.withProtocol(listener.externalProtocol.name())
          if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
            awsListener.withInstanceProtocol(listener.internalProtocol.name())
          } else {
            awsListener.withInstanceProtocol(listener.externalProtocol.name())
          }
          if (listener.sslCertificateId) {
            task.updateStatus BASE_PHASE, "Attaching listener with SSL ServerCertificate: ${listener.sslCertificateId}"
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

      def securityGroupNamesToIds = regionScopedProvider.securityGroupService.getSecurityGroupIds(description.securityGroups, description.vpcId)
      def securityGroups = securityGroupNamesToIds.values()
      log.info("security groups on {} {}", description.name, securityGroups)
      String dnsName
      if (!loadBalancer) {
        task.updateStatus BASE_PHASE, "Creating ${loadBalancerName} in ${description.credentials.name}:${region}..."
        def subnetIds = []
        if (description.subnetType) {
          subnetIds = regionScopedProvider.subnetAnalyzer.getSubnetIdsForZones(availabilityZones,
                  description.subnetType, SubnetTarget.ELB, 1)
        }

        //require that we have addAppGroupToServerGroup as well as createLoadBalancerIngressPermissions
        // set since the load balancer ingress assumes that application group is the target of those
        // permissions
        if (deployDefaults.createLoadBalancerIngressPermissions && deployDefaults.addAppGroupToServerGroup) {
          String application = null
          try {
            application = Names.parseName(description.name).getApp() ?: Names.parseName(description.clusterName).getApp()
            IngressLoadBalancerGroupResult ingressLoadBalancerResult = ingressApplicationLoadBalancerGroup(
              description,
              application,
              region,
              listeners,
              securityGroupLookupFactory
            )

            securityGroupNamesToIds.put(ingressLoadBalancerResult.groupName, ingressLoadBalancerResult.groupId)
            task.updateStatus BASE_PHASE, "Authorized app ELB Security Group ${ingressLoadBalancerResult}"
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
            AWSShield shieldClient = amazonClientProvider.getAmazonShield(description.credentials, region)
            shieldClient.createProtection(
              new CreateProtectionRequest()
                .withName(loadBalancerName)
                .withResourceArn(loadBalancerArn(description.credentials.accountId, region, loadBalancerName))
            )
            task.updateStatus BASE_PHASE, "AWS Shield configured for ${loadBalancerName} in ${region}."
          } catch (Exception e) {
            log.error("Failed to enable AWS Shield protection on $loadBalancerName", e)
            task.updateStatus BASE_PHASE, "Failed to configure AWS Shield for ${loadBalancerName} in ${region}."
          }
        }
      } else {
        dnsName = loadBalancer.DNSName
        LoadBalancerUpsertHandler.updateLoadBalancer(loadBalancing, loadBalancer, listeners, securityGroups)
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

      CrossZoneLoadBalancing crossZoneLoadBalancing = null
      ConnectionDraining connectionDraining = null

      if (loadBalancer) {
        def currentAttributes = loadBalancing.describeLoadBalancerAttributes(new DescribeLoadBalancerAttributesRequest().withLoadBalancerName(loadBalancerName)).loadBalancerAttributes

        Boolean crossZoneBalancingEnabled = [description.crossZoneBalancing, currentAttributes?.crossZoneLoadBalancing?.enabled, deployDefaults.loadBalancing.crossZoneBalancingDefault].findResult(Closure.IDENTITY)

        if (crossZoneBalancingEnabled != currentAttributes?.crossZoneLoadBalancing?.enabled) {
          crossZoneLoadBalancing = new CrossZoneLoadBalancing(enabled: crossZoneBalancingEnabled)
        }

        Boolean connectionDrainingEnabled = [description.connectionDraining, currentAttributes?.connectionDraining?.enabled, deployDefaults.loadBalancing.connectionDrainingDefault].findResult(Closure.IDENTITY)
        Integer deregistrationDelay = [description.deregistrationDelay, currentAttributes?.connectionDraining?.timeout, deployDefaults.loadBalancing.deregistrationDelayDefault].findResult(Closure.IDENTITY)

        if (connectionDrainingEnabled != currentAttributes?.connectionDraining?.enabled || deregistrationDelay != currentAttributes?.connectionDraining?.timeout) {
          connectionDraining = new ConnectionDraining(
            enabled: connectionDrainingEnabled,
            timeout: deregistrationDelay)
        }
      } else {
        crossZoneLoadBalancing = new CrossZoneLoadBalancing(enabled: [description.crossZoneBalancing, deployDefaults.loadBalancing.crossZoneBalancingDefault].findResult(Boolean.TRUE, Closure.IDENTITY))
        connectionDraining = new ConnectionDraining(
          enabled: [description.connectionDraining, deployDefaults.loadBalancing.connectionDrainingDefault].findResult(Boolean.FALSE, Closure.IDENTITY),
          timeout: [description.deregistrationDelay, deployDefaults.loadBalancing.deregistrationDelayDefault].findResult(Closure.IDENTITY))
      }

      if (crossZoneLoadBalancing != null || connectionDraining != null) {
        LoadBalancerAttributes attributes = new LoadBalancerAttributes()
        if (crossZoneLoadBalancing) {
          attributes.setCrossZoneLoadBalancing(crossZoneLoadBalancing)
        }
        if (connectionDraining) {
          attributes.setConnectionDraining(connectionDraining)
        }
        // Apply balancing opinions...
        loadBalancing.modifyLoadBalancerAttributes(
          new ModifyLoadBalancerAttributesRequest(loadBalancerName: loadBalancerName)
            .withLoadBalancerAttributes(attributes)
          )
      }


      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.name} in ${region}."
      operationResult.loadBalancers[region] = new LoadBalancer(loadBalancerName, dnsName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }

  private static IngressLoadBalancerGroupResult ingressApplicationLoadBalancerGroup(UpsertAmazonLoadBalancerClassicDescription description,
                                                                                    String application,
                                                                                    String region,
                                                                                    List<Listener> loadBalancerListeners,
                                                                                    SecurityGroupLookupFactory securityGroupLookupFactory) throws FailedSecurityGroupIngressException {
    SecurityGroupLookup securityGroupLookup = securityGroupLookupFactory.getInstance(region)

    // 1. get app load balancer security group & app security group. create if doesn't exist
    SecurityGroupUpdater applicationLoadBalancerSecurityGroupUpdater = getOrCreateSecurityGroup(
      application + "-elb",
      region,
      "Application ELB Security Group for $application",
      description,
      securityGroupLookup
    )

    SecurityGroupUpdater applicationSecurityGroupUpdater = getOrCreateSecurityGroup(
      application,
      region,
      "Application Security Group for $application",
      description,
      securityGroupLookup
    )

    def source = applicationLoadBalancerSecurityGroupUpdater.securityGroup
    def target = applicationSecurityGroupUpdater.securityGroup
    List<IpPermission> currentPermissions = SecurityGroupIngressConverter.flattenPermissions(target)
    List<IpPermission> targetPermissions = loadBalancerListeners.collect {
      newIpPermissionWithSourceAndPort(source.groupId, it.getInstancePort())
    }

    if (!includesRulesWithHealthCheckPort(targetPermissions, description, source) && description.healthCheckPort) {
      targetPermissions.add(
        newIpPermissionWithSourceAndPort(source.groupId, description.healthCheckPort)
      )
    }

    filterOutExistingPermissions(targetPermissions, currentPermissions)
    if (targetPermissions) {
      try {
        applicationSecurityGroupUpdater.addIngress(targetPermissions)
      } catch (Exception e) {
        throw new FailedSecurityGroupIngressException(e)
      }
    }

    return new IngressLoadBalancerGroupResult(source.groupId, source.groupName)
  }

  private static class IngressLoadBalancerGroupResult {
    private final String groupId
    private final String groupName

    IngressLoadBalancerGroupResult(String groupId, String groupName) {
      this.groupId = groupId
      this.groupName = groupName
    }

    @Override
    String toString() {
      return "IngressLoadBalancerGroupResult{" +
        "groupId='" + groupId + '\'' +
        ", groupName='" + groupName + '\'' +
        '}'
    }
  }

  private static IpPermission newIpPermissionWithSourceAndPort(String sourceGroupId, int port) {
    return new IpPermission(
      ipProtocol: "tcp",
      fromPort: port,
      toPort: port,
      userIdGroupPairs: [
        new UserIdGroupPair().withGroupId(sourceGroupId)
      ]
    )
  }

  private static boolean includesRulesWithHealthCheckPort(List<IpPermission> targetPermissions,
                                                          UpsertAmazonLoadBalancerClassicDescription description,
                                                          SecurityGroup source) {
    return targetPermissions.find {
      description.healthCheckPort && it.fromPort == description.healthCheckPort &&
        it.toPort == description.healthCheckPort && source.groupId in it.userIdGroupPairs*.groupId
    } != null
  }

  private static SecurityGroupUpdater getOrCreateSecurityGroup(String groupName,
                                                               String region,
                                                               String descriptionText,
                                                               UpsertAmazonLoadBalancerClassicDescription description,
                                                               SecurityGroupLookup securityGroupLookup) {
    SecurityGroupUpdater securityGroupUpdater = null
    OperationPoller.retryWithBackoff({
      securityGroupUpdater = securityGroupLookup.getSecurityGroupByName(
        description.credentialAccount,
        groupName,
        description.vpcId
      ).orElse(null)

      if (!securityGroupUpdater) {
        securityGroupUpdater = securityGroupLookup.createSecurityGroup(
          new UpsertSecurityGroupDescription(
            name: groupName,
            description: descriptionText,
            vpcId: description.vpcId,
            region: region,
            credentials: description.credentials
          )
        )
      }
    }, 500, 3)

    return securityGroupUpdater
  }

  private static void filterOutExistingPermissions(List<IpPermission> permissionsToAdd,
                                                   List<IpPermission> existingPermissions) {
    permissionsToAdd.each { permission ->
      permission.getUserIdGroupPairs().removeIf { pair ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            pair.groupId && pair.groupId in p.userIdGroupPairs*.groupId
        } != null
      }

      permission.getIpv4Ranges().removeIf { range ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            range in p.ipv4Ranges
        } != null
      }

      permission.getIpv6Ranges().removeIf { range ->
        existingPermissions.find { p ->
          p.getFromPort() == permission.getFromPort() &&
            p.getToPort() == permission.getToPort() &&
            range in p.ipv6Ranges
        } != null
      }
    }

    permissionsToAdd.removeIf { permission -> !permission.userIdGroupPairs }
  }

  private static String loadBalancerArn(String accountId, String region, String name) {
    return "arn:aws:elasticloadbalancing:$accountId:$region:loadbalancer/$name"
  }

  @InheritConstructors
  static class FailedSecurityGroupIngressException extends Exception {}
}
