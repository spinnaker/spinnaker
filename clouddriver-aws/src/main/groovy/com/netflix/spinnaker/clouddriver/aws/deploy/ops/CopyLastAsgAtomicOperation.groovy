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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest
import com.amazonaws.services.ec2.model.LaunchTemplateVersion
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator
import com.netflix.spinnaker.clouddriver.aws.model.SubnetData
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired

class CopyLastAsgAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_ASG"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  BasicAmazonDeployHandler basicAmazonDeployHandler

  @Autowired
  BasicAmazonDeployDescriptionValidator basicAmazonDeployDescriptionValidator

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  LocalFileUserDataProperties localFileUserDataProperties

  final BasicAmazonDeployDescription description

  CopyLastAsgAtomicOperation(BasicAmazonDeployDescription description) {
    this.description = description
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Copy Last ASG Operation..."

    Set<String> targetRegions = description.availabilityZones?.keySet()
    if (description.source.region && description.source.account && description.source.asgName) {
      def sourceName = Names.parseName(description.source.asgName)
      description.application = description.application != null ? description.application : sourceName.app
      description.stack = description.stack != null ? description.stack : sourceName.stack
      description.freeFormDetails = description.freeFormDetails != null ? description.freeFormDetails : sourceName.detail
      targetRegions = targetRegions ?: [description.source.region]
    }
    DeploymentResult result = new DeploymentResult()
    def cluster = new AutoScalingGroupNameBuilder(appName: description.application, stack: description.stack, detail: description.freeFormDetails).buildGroupName()
    List<BasicAmazonDeployDescription> deployDescriptions = targetRegions.collect { String targetRegion ->
      AutoScalingGroup ancestorAsg = null
      def sourceRegion
      def sourceAsgCredentials
      if (description.source.account && description.source.region && description.source.asgName) {
        sourceRegion = description.source.region
        sourceAsgCredentials = credentialsRepository.getOne(description.source.account)
        def sourceAutoScaling = amazonClientProvider.getAutoScaling(sourceAsgCredentials, sourceRegion, true)
        def request = new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [description.source.asgName])
        List<AutoScalingGroup> ancestorAsgs = sourceAutoScaling.describeAutoScalingGroups(request).autoScalingGroups
        ancestorAsg = ancestorAsgs.getAt(0)
      } else {
        sourceRegion = targetRegion
        sourceAsgCredentials = description.credentials
      }
      boolean sourceIsTarget = sourceRegion == targetRegion && sourceAsgCredentials.name == description.credentials.name
      def sourceRegionScopedProvider = regionScopedProviderFactory.forRegion(sourceAsgCredentials, sourceRegion)
      Closure<List<String>> translateSecurityGroupIds = { List<String> ids ->
        if (!sourceIsTarget) {
          return ids
        }
        if (!ids) {
          return ids
        }
        sourceRegionScopedProvider.getSecurityGroupService().getSecurityGroupNamesFromIds(ids).keySet().toList()
      }

      BasicAmazonDeployDescription newDescription = description.clone()
      if (!ancestorAsg) {
        task.updateStatus BASE_PHASE, "Looking up last ASG in ${sourceRegion} for ${cluster}."
        def latestServerGroupName = sourceRegionScopedProvider.AWSServerGroupNameResolver.resolveLatestServerGroupName(cluster)
        if (latestServerGroupName) {
          ancestorAsg = sourceRegionScopedProvider.asgService.getAutoScalingGroup(latestServerGroupName)
        }

        if (ancestorAsg) {
          newDescription.source = new BasicAmazonDeployDescription.Source(
            sourceAsgCredentials.name,
            sourceRegionScopedProvider.region,
            ancestorAsg.autoScalingGroupName,
            null // we will already pull the capacity from this ASG
          )
          task.updateStatus BASE_PHASE, "Using ${sourceRegion}/${ancestorAsg.autoScalingGroupName} as source."
        }
      }

      if (ancestorAsg) {
        String iamInstanceProfile
        String imageId
        String instanceType
        String spotPrice
        String keyName
        String kernelId
        String ramdiskId
        String userData
        String classicLinkVPCId = null

        Boolean ebsOptimized
        Boolean instanceMonitoring
        Boolean associatePublicIpAddress

        List<String> securityGroups
        List<String> classicLinkVPCSecurityGroups = null
        if (ancestorAsg.launchTemplate != null) {
          LaunchTemplateVersion launchTemplateVersion = sourceRegionScopedProvider
            .launchTemplateService.getLaunchTemplateVersion(ancestorAsg.launchTemplate)
            .orElseThrow({
              new IllegalStateException("Requested launch template $ancestorAsg.launchTemplate was not found")
            })

          def launchTemplateData = launchTemplateVersion.launchTemplateData

          newDescription.setLaunchTemplate = true
          imageId = launchTemplateData.imageId
          keyName = launchTemplateData.keyName
          kernelId = launchTemplateData.kernelId
          userData = launchTemplateData.userData
          ramdiskId = launchTemplateData.ramDiskId
          instanceType = launchTemplateData.instanceType
          securityGroups = launchTemplateData.securityGroups
          ebsOptimized = launchTemplateData.ebsOptimized
          iamInstanceProfile = launchTemplateData.iamInstanceProfile?.name
          instanceMonitoring = launchTemplateData.monitoring?.enabled
          spotPrice = launchTemplateData.instanceMarketOptions?.spotOptions?.maxPrice
          newDescription.requireIMDSv2 = description.requireIMDSv2 != null ? description.requireIMDSv2 : launchTemplateData.metadataOptions?.httpTokens == "required"
          if (!launchTemplateData.networkInterfaces?.empty && launchTemplateData.networkInterfaces*.associatePublicIpAddress?.any()) {
            associatePublicIpAddress = true
          }
          if (!launchTemplateData.networkInterfaces?.empty) {
            // Network interfaces are the source of truth for launch template security groups
            def networkInterface = launchTemplateData.networkInterfaces.find({it.deviceIndex == 0 })
            if (networkInterface != null) {
              securityGroups = networkInterface.groups
              newDescription.associateIPv6Address = networkInterface.getIpv6AddressCount() > 0 ? true : false
            }
          }

          // unlimited CPU credits is not applicable for all instance types. So, simply use the incoming request's value to keep the description valid.
          newDescription.unlimitedCpuCredits = description.unlimitedCpuCredits
        } else {
          def ancestorLaunchConfiguration = sourceRegionScopedProvider
            .asgService.getLaunchConfiguration(ancestorAsg.launchConfigurationName)

          keyName = ancestorLaunchConfiguration.keyName
          imageId = ancestorLaunchConfiguration.imageId
          kernelId = ancestorLaunchConfiguration.kernelId
          userData = ancestorLaunchConfiguration.userData
          ramdiskId = ancestorLaunchConfiguration.ramdiskId
          spotPrice = ancestorLaunchConfiguration.spotPrice
          ebsOptimized = ancestorLaunchConfiguration.ebsOptimized
          instanceType = ancestorLaunchConfiguration.instanceType
          securityGroups = ancestorLaunchConfiguration.securityGroups
          classicLinkVPCId = ancestorLaunchConfiguration.classicLinkVPCId
          iamInstanceProfile = ancestorLaunchConfiguration.iamInstanceProfile
          instanceMonitoring = ancestorLaunchConfiguration.instanceMonitoring?.enabled
          associatePublicIpAddress = ancestorLaunchConfiguration.associatePublicIpAddress
          classicLinkVPCSecurityGroups = ancestorLaunchConfiguration.classicLinkVPCSecurityGroups
        }

        if (ancestorAsg.VPCZoneIdentifier) {
          task.updateStatus BASE_PHASE, "Looking up subnet type..."
          newDescription.subnetType = description.subnetType != null ? description.subnetType : getPurposeForSubnet(sourceRegion, ancestorAsg.VPCZoneIdentifier.tokenize(',').getAt(0))
          task.updateStatus BASE_PHASE, "Found: ${newDescription.subnetType}."
        }

        newDescription.iamRole = description.iamRole ?: iamInstanceProfile
        newDescription.amiName = description.amiName ?: imageId
        newDescription.availabilityZones = [(targetRegion): description.availabilityZones[targetRegion] ?: ancestorAsg.availabilityZones]
        newDescription.instanceType = description.instanceType ?: instanceType
        newDescription.loadBalancers = description.loadBalancers != null ? description.loadBalancers : ancestorAsg.loadBalancerNames
        newDescription.targetGroups = description.targetGroups
        if (newDescription.targetGroups == null && ancestorAsg.targetGroupARNs && ancestorAsg.targetGroupARNs.size() > 0) {
          def targetGroups = sourceRegionScopedProvider.getAmazonElasticLoadBalancingV2(true).describeTargetGroups(new DescribeTargetGroupsRequest().withTargetGroupArns(ancestorAsg.targetGroupARNs)).targetGroups
          def targetGroupNames = targetGroups.collect { it.targetGroupName }
          newDescription.targetGroups = targetGroupNames
        }

        newDescription.securityGroups = description.securityGroups != null ? description.securityGroups : translateSecurityGroupIds(securityGroups)
        newDescription.capacity.min = description.capacity?.min != null ? description.capacity.min : ancestorAsg.minSize
        newDescription.capacity.max = description.capacity?.max != null ? description.capacity.max : ancestorAsg.maxSize
        newDescription.capacity.desired = description.capacity?.desired != null ? description.capacity.desired : ancestorAsg.desiredCapacity
        newDescription.keyPair = description.keyPair ?: (sourceIsTarget ? keyName : description.credentials.defaultKeyPair)
        newDescription.associatePublicIpAddress = description.associatePublicIpAddress != null ? description.associatePublicIpAddress : associatePublicIpAddress
        newDescription.cooldown = description.cooldown != null ? description.cooldown : ancestorAsg.defaultCooldown
        newDescription.enabledMetrics = description.enabledMetrics != null ? description.enabledMetrics : ancestorAsg.enabledMetrics*.metric
        newDescription.healthCheckGracePeriod = description.healthCheckGracePeriod != null ? description.healthCheckGracePeriod : ancestorAsg.healthCheckGracePeriod
        newDescription.healthCheckType = description.healthCheckType ?: ancestorAsg.healthCheckType
        newDescription.suspendedProcesses = description.suspendedProcesses != null ? description.suspendedProcesses : ancestorAsg.suspendedProcesses*.processName
        newDescription.terminationPolicies = description.terminationPolicies != null ? description.terminationPolicies : ancestorAsg.terminationPolicies
        newDescription.kernelId = description.kernelId ?: (kernelId ?: null)
        newDescription.ramdiskId = description.ramdiskId ?: (ramdiskId ?: null)
        newDescription.instanceMonitoring = description.instanceMonitoring != null ? description.instanceMonitoring : instanceMonitoring
        newDescription.ebsOptimized = description.ebsOptimized != null ? description.ebsOptimized : ebsOptimized
        newDescription.classicLinkVpcId = description.classicLinkVpcId != null ? description.classicLinkVpcId : classicLinkVPCId
        newDescription.classicLinkVpcSecurityGroups = description.classicLinkVpcSecurityGroups != null ? description.classicLinkVpcSecurityGroups : translateSecurityGroupIds(classicLinkVPCSecurityGroups)
        newDescription.tags = description.tags != null ? description.tags : ancestorAsg.tags.collectEntries {
          [(it.getKey()): it.getValue()]
        }

        /*
          Copy over the ancestor user data only if the UserDataProviders behavior is disabled and no user data is provided
          on this request.
          This is to avoid having duplicate user data.
         */
        if (localFileUserDataProperties && !localFileUserDataProperties.enabled) {
          newDescription.base64UserData = description.base64UserData != null ? description.base64UserData : userData
        }

        if (description.spotPrice == null) {
          newDescription.spotPrice = spotPrice
        } else if (description.spotPrice) {
          newDescription.spotPrice = description.spotPrice
        } else { // ""
          newDescription.spotPrice = null
        }
      }

      task.updateStatus BASE_PHASE, "Validating clone configuration for $targetRegion."
      def errors = new DescriptionValidationErrors(newDescription)
      basicAmazonDeployDescriptionValidator.validate(priorOutputs, newDescription, errors)
      if (errors.hasErrors()) {
        throw new DescriptionValidationException(errors)
      }

      return newDescription
    }

    for (BasicAmazonDeployDescription newDescription : deployDescriptions) {
      String targetRegion = newDescription.availabilityZones.keySet()[0]
      task.updateStatus BASE_PHASE, "Initiating deployment in $targetRegion."
      def thisResult = basicAmazonDeployHandler.handle(newDescription, priorOutputs)

      result.serverGroupNames.addAll(thisResult.serverGroupNames)
      result.deployedNames.addAll(thisResult.deployedNames)
      result.deployments.addAll(thisResult.deployments)
      result.createdArtifacts.addAll(thisResult.createdArtifacts)
      result.messages.addAll(thisResult.messages)
      thisResult.serverGroupNameByRegion.entrySet().each { result.serverGroupNameByRegion[it.key] = it.value }
      thisResult.deployedNamesByLocation.entrySet().each { result.deployedNamesByLocation[it.key] = it.value }

      task.updateStatus BASE_PHASE, "Deployment complete in $targetRegion. New ASGs = ${result.serverGroupNames}"
    }
    task.updateStatus BASE_PHASE, "Finished copying last ASG for ${cluster}. New ASGs = ${result.serverGroupNames}."

    result
  }

  String getPurposeForSubnet(String region, String subnetId) {
    def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region, true)
    def result = amazonEC2.describeSubnets(new DescribeSubnetsRequest().withSubnetIds(subnetId))
    if (result && result.subnets) {
      def data = SubnetData.from(result.subnets.first())
      return data.purpose
    }
    return null
  }
}
