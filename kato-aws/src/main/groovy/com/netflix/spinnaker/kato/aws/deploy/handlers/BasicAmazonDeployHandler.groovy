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


package com.netflix.spinnaker.kato.aws.deploy.handlers

import com.amazonaws.services.autoscaling.model.BlockDeviceMapping
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.google.common.annotations.VisibleForTesting
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.AmiIdResolver
import com.netflix.spinnaker.kato.aws.deploy.AutoScalingWorker
import com.netflix.spinnaker.kato.aws.deploy.ResolvedAmiResult
import com.netflix.spinnaker.kato.aws.deploy.description.BasicAmazonDeployDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerResult
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import groovy.transform.PackageScope

class BasicAmazonDeployHandler implements DeployHandler<BasicAmazonDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final List<UserDataProvider> userDataProviders
  private final AmazonClientProvider amazonClientProvider
  private final RegionScopedProviderFactory regionScopedProviderFactory
  private final AccountCredentialsRepository accountCredentialsRepository
  private final KatoAWSConfig.DeployDefaults deployDefaults

  BasicAmazonDeployHandler(List<UserDataProvider> userDataProviders,
                           AmazonClientProvider amazonClientProvider,
                           RegionScopedProviderFactory regionScopedProviderFactory,
                           AccountCredentialsRepository accountCredentialsRepository,
                           KatoAWSConfig.DeployDefaults deployDefaults) {
    this.userDataProviders = userDataProviders
    this.amazonClientProvider = amazonClientProvider
    this.regionScopedProviderFactory = regionScopedProviderFactory
    this.accountCredentialsRepository = accountCredentialsRepository
    this.deployDefaults = deployDefaults
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()
    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key

      def sourceRegionScopedProvider = buildSourceRegionScopedProvider(task, description.source)
      description = copySourceAttributes(
        sourceRegionScopedProvider, amazonClientProvider, description.source.asgName, description
      )

      List<String> availabilityZones = entry.value

      // Get the properly typed version of the description's subnetType
      def subnetType = description.subnetType

      // Get the list of load balancers that were created as part of this conglomerate job to apply to the ASG.
      List<UpsertAmazonLoadBalancerResult.LoadBalancer> suppliedLoadBalancers = (List<UpsertAmazonLoadBalancerResult.LoadBalancer>) priorOutputs.findAll {
        it instanceof UpsertAmazonLoadBalancerResult
      }?.loadBalancers?.getAt(region)

      if (!description.loadBalancers) {
        description.loadBalancers = []
      }
      description.loadBalancers.addAll suppliedLoadBalancers?.name

      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region)
      def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region)
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)

      if (!description.blockDevices) {
        def blockDeviceConfig = deployDefaults.instanceClassBlockDevices.find {
          it.handlesInstanceType(description.instanceType)
        }
        if (blockDeviceConfig) {
          description.blockDevices = blockDeviceConfig.blockDevices
        }
      }

      // find by 1) result of a previous step (we performed allow launch)
      //         2) explicitly granted launch permission
      //            (making this the default because AllowLaunch will always
      //             stick an explicit launch permission on the image)
      //         3) owner
      //         4) global
      ResolvedAmiResult ami = priorOutputs.find({
        it instanceof ResolvedAmiResult && it.region == region && it.amiName == description.amiName
      }) ?:
        AmiIdResolver.resolveAmiId(amazonEC2, region, description.amiName, null, description.credentials.accountId) ?:
          AmiIdResolver.resolveAmiId(amazonEC2, region, description.amiName, description.credentials.accountId) ?:
            AmiIdResolver.resolveAmiId(amazonEC2, region, description.amiName, null, null)

      if (!ami) {
        throw new IllegalArgumentException("unable to resolve AMI imageId from $description.amiName")
      }

      def account = accountCredentialsRepository.getOne(description.credentials.name)
      if (!(account instanceof NetflixAmazonCredentials)) {
        throw new IllegalArgumentException("Unsupported account type ${account.class.simpleName} for this operation")
      }

      def autoScalingWorker = new AutoScalingWorker(
        application: description.application,
        region: region,
        environment: description.credentials.name,
        stack: description.stack,
        freeFormDetails: description.freeFormDetails,
        ami: ami.amiId,
        minInstances: description.capacity.min,
        maxInstances: description.capacity.max,
        desiredInstances: description.capacity.desired,
        securityGroups: description.securityGroups,
        iamRole: description.iamRole ?: deployDefaults.iamRole,
        keyPair: description.keyPair ?: account?.defaultKeyPair,
        ignoreSequence: description.ignoreSequence,
        startDisabled: description.startDisabled,
        associatePublicIpAddress: description.associatePublicIpAddress,
        blockDevices: description.blockDevices,
        instanceType: description.instanceType,
        availabilityZones: availabilityZones,
        subnetType: subnetType,
        amazonEC2: amazonEC2,
        autoScaling: autoScaling,
        loadBalancers: description.loadBalancers,
        userDataProviders: userDataProviders,
        securityGroupService: regionScopedProvider.securityGroupService,
        cooldown: description.cooldown,
        healthCheckGracePeriod: description.healthCheckGracePeriod,
        healthCheckType: description.healthCheckType,
        terminationPolicies: description.terminationPolicies,
        spotPrice: description.spotPrice,
        suspendedProcesses: description.suspendedProcesses,
        ramdiskId: description.ramdiskId,
        instanceMonitoring: description.instanceMonitoring,
        ebsOptimized: description.ebsOptimized,
      )

      def asgName = autoScalingWorker.deploy()

      deploymentResult.serverGroupNames << "${region}:${asgName}".toString()
      deploymentResult.serverGroupNameByRegion[region] = asgName

      copyScalingPoliciesAndScheduledActions(
        task, sourceRegionScopedProvider, description.credentials, description.source.asgName, region, asgName
      )
    }

    return deploymentResult
  }

  @VisibleForTesting
  @PackageScope
  BasicAmazonDeployDescription copySourceAttributes(RegionScopedProviderFactory.RegionScopedProvider sourceRegionScopedProvider,
                                                    AmazonClientProvider amazonClientProvider,
                                                    String sourceAsgName,
                                                    BasicAmazonDeployDescription description) {
    if (!sourceRegionScopedProvider) {
      return description
    }

    description = description.clone()

    def sourceAutoScaling = amazonClientProvider.getAutoScaling(
      sourceRegionScopedProvider.amazonCredentials,
      sourceRegionScopedProvider.region
    )
    def ancestorAsgs = sourceAutoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [sourceAsgName])
    ).autoScalingGroups
    def sourceAsg = ancestorAsgs.getAt(0)

    if (!sourceAsg?.launchConfigurationName) {
      return description
    }

    def sourceLaunchConfiguration = sourceRegionScopedProvider.asgService.getLaunchConfiguration(
      sourceAsg.launchConfigurationName
    )

    description.blockDevices = description.blockDevices != null ? description.blockDevices : convertBlockDevices(sourceLaunchConfiguration.blockDeviceMappings)
    description.spotPrice = description.spotPrice ?: sourceLaunchConfiguration.spotPrice

    return description
  }

  @VisibleForTesting
  @PackageScope
  void copyScalingPoliciesAndScheduledActions(Task task,
                                              RegionScopedProviderFactory.RegionScopedProvider sourceRegionScopedProvider,
                                              NetflixAmazonCredentials targetCredentials,
                                              String sourceAsgName,
                                              String targetRegion,
                                              String targetAsgName) {
    if (!sourceRegionScopedProvider) {
      return
    }

    def asgReferenceCopier = sourceRegionScopedProvider.getAsgReferenceCopier(targetCredentials, targetRegion)
    asgReferenceCopier.copyScalingPoliciesWithAlarms(task, sourceAsgName, targetAsgName)
    asgReferenceCopier.copyScheduledActionsForAsg(task, sourceAsgName, targetAsgName)
  }

  @VisibleForTesting
  @PackageScope
  static List<AmazonBlockDevice> convertBlockDevices(List<BlockDeviceMapping> blockDeviceMappings) {
    blockDeviceMappings.collect {
      def device = new AmazonBlockDevice(deviceName: it.deviceName, virtualName: it.virtualName)
      it.ebs?.with {
        device.iops = iops
        device.deleteOnTermination = deleteOnTermination
        device.size = volumeSize
        device.volumeType = volumeType
        device.snapshotId = snapshotId
      }
      device
    }
  }

  private RegionScopedProviderFactory.RegionScopedProvider buildSourceRegionScopedProvider(Task task,
                                                                                           BasicAmazonDeployDescription.Source source) {
    if (source.account && source.region && source.asgName) {
      def sourceRegion = source.region
      def sourceAsgCredentials = accountCredentialsRepository.getOne(source.account) as NetflixAmazonCredentials
      def regionScopedProvider = regionScopedProviderFactory.forRegion(sourceAsgCredentials, sourceRegion)

      def sourceAsgs = regionScopedProvider.asgService.amazonAutoScaling.describeAutoScalingGroups(
        new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [source.asgName])
      )

      if (!sourceAsgs.autoScalingGroups) {
        task.updateStatus BASE_PHASE, "Unable to locate source asg (${source.account}:${source.region}:${source.asgName})"
        return null
      }

      return regionScopedProvider
    }

    return null
  }
}
