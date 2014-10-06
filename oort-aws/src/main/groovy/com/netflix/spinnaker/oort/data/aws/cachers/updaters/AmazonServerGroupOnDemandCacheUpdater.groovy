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

package com.netflix.spinnaker.oort.data.aws.cachers.updaters

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.*
import com.netflix.spinnaker.oort.model.OnDemandCacheUpdater
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * An on-demand cache updater for Amazon AutoScaling groups.
 *
 * KEY: AmazonServerGroup
 */
//@Component
class AmazonServerGroupOnDemandCacheUpdater implements OnDemandCacheUpdater {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  ApplicationContext applicationContext

  @Override
  boolean handles(String type) {
    type == "AmazonServerGroup"
  }

  @Override
  void handle(String type, Map<String, ? extends Object> data) {
    if (!data.containsKey("asgName")) {
      return
    }
    if (!data.containsKey("account")) {
      return
    }
    if (!data.containsKey("region")) {
      return
    }

    def asgName = data.asgName as String
    def account = data.account as String
    def region = data.region as String

    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!credentials) {
      return
    }
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      return
    }

    def asg = updateCluster asgName, credentials, region, (data.evict as boolean ?: false)
    if (asg) {
      updateInstances asg, credentials, region
      def launchConfig = updateLaunchConfig(asg, credentials, region)
      if (launchConfig) {
        updateImage launchConfig, credentials, region
      }
    }
  }

  AutoScalingGroup updateCluster(String asgName, NetflixAmazonCredentials credentials, String region, boolean evict) {
    def clusterCachingAgent = getWiredClusterCachingAgent(credentials, region)

    if (evict) {
      clusterCachingAgent.removeServerGroup(credentials, asgName, region)
    } else {
      Names names = Names.parseName(asgName)
      def autoScaling = amazonClientProvider.getAutoScaling(credentials, region, true)
      def request = new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: [asgName])
      def result = autoScaling.describeAutoScalingGroups(request)
      if (result.autoScalingGroups) {
        AutoScalingGroup autoScalingGroup = result.autoScalingGroups.get(0)
        clusterCachingAgent.loadApp(names)
        clusterCachingAgent.loadCluster(credentials, autoScalingGroup, names, region)
        clusterCachingAgent.loadServerGroups(credentials, autoScalingGroup, names, region)
        return autoScalingGroup
      }
    }
    null
  }

  void updateInstances(AutoScalingGroup asg, NetflixAmazonCredentials credentials, String region) {
    def instanceCachingAgent = getWiredInstanceCachingAgent(credentials, region)

    def instanceIds = asg.instances.instanceId
    if (instanceIds) {
      def ec2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
      def result = ec2.describeInstances(new DescribeInstancesRequest(instanceIds: instanceIds))
      for (reservation in result.reservations) {
        for (instance in reservation.instances) {
          instanceCachingAgent.loadNewInstance(credentials, instance, region)
        }
      }
    }
  }

  LaunchConfiguration updateLaunchConfig(AutoScalingGroup asg, NetflixAmazonCredentials credentials, String region) {
    def launchConfigCachingAgent = getWiredLaunchConfigCachingAgent(credentials, region)

    def autoScaling = amazonClientProvider.getAutoScaling(credentials, region, true)
    def result = autoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest(launchConfigurationNames: [asg.launchConfigurationName]))
    if (result.launchConfigurations) {
      def launchConfig = result.launchConfigurations.get(0)
      launchConfigCachingAgent.loadNewLaunchConfig(launchConfig, region)
      return launchConfig
    }
    null
  }

  void updateImage(LaunchConfiguration launchConfiguration, NetflixAmazonCredentials credentials, String region) {
    def imageCachingAgent = getWiredImageCachingAgent(credentials, region)

    def ec2 = amazonClientProvider.getAmazonEC2(credentials, region, true)
    def result = ec2.describeImages(new DescribeImagesRequest(imageIds: [launchConfiguration.imageId]))
    if (result.images) {
      def image = result.images.get(0)
      imageCachingAgent.loadNewImage(image, region)
    }
  }

  private def getWiredClusterCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (ClusterCachingAgent) autowire(InfrastructureCachingAgentFactory.getClusterCachingAgent(credentials, region))
  }

  private def getWiredInstanceCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (InstanceCachingAgent) autowire(InfrastructureCachingAgentFactory.getInstanceCachingAgent(credentials, region))
  }

  private def getWiredLaunchConfigCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (LaunchConfigCachingAgent) autowire(InfrastructureCachingAgentFactory.getLaunchConfigCachingAgent(credentials, region))
  }

  private def getWiredImageCachingAgent(NetflixAmazonCredentials credentials, String region) {
    (ImageCachingAgent) autowire(InfrastructureCachingAgentFactory.getImageCachingAgent(credentials, region))
  }


  def autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean obj
    obj
  }
}
