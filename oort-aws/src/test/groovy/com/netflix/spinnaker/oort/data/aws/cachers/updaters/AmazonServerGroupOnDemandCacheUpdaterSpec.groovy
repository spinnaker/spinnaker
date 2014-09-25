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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.Reservation
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.cachers.ClusterCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.ImageCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.InfrastructureCachingAgentFactory
import com.netflix.spinnaker.oort.data.aws.cachers.InstanceCachingAgent
import com.netflix.spinnaker.oort.data.aws.cachers.LaunchConfigCachingAgent
import com.netflix.spinnaker.oort.model.CacheService
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonServerGroupOnDemandCacheUpdaterSpec extends Specification {

  @Shared
  CacheService cacheService

  @Shared
  AmazonClientProvider amazonClientProvider

  @Shared
  NetflixAmazonCredentials credentials

  @Shared
  ConfigurableListableBeanFactory beanFactory = Stub(ConfigurableListableBeanFactory)

  @Subject
  AmazonServerGroupOnDemandCacheUpdater updater

  void setup() {
    GroovyMock(InfrastructureCachingAgentFactory, global: true)

    credentials = Stub(NetflixAmazonCredentials)
    AccountCredentialsProvider accountCredentialsProvider = Stub(AccountCredentialsProvider)
    accountCredentialsProvider.getCredentials(_) >> credentials
    ApplicationContext ctx = Stub(ApplicationContext) {
      getAutowireCapableBeanFactory() >> beanFactory
    }

    cacheService = Mock(CacheService)
    amazonClientProvider = Stub(AmazonClientProvider)
    updater = new AmazonServerGroupOnDemandCacheUpdater(accountCredentialsProvider: accountCredentialsProvider, amazonClientProvider: amazonClientProvider, applicationContext: ctx)
  }

  void "should call the cluster caching agent load operations when updating the cluster"() {
    setup:
    def mockAgent = Mock(ClusterCachingAgent)
    InfrastructureCachingAgentFactory.getClusterCachingAgent(credentials, region) >> mockAgent
    def autoScaling = Mock(AmazonAutoScaling)
    amazonClientProvider.getAutoScaling(credentials, region, true) >> autoScaling

    when:
    updater.updateCluster(asgName, credentials, region, false)

    then:
    1 * autoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest req ->
      assert req.autoScalingGroupNames[0] == asgName

      new DescribeAutoScalingGroupsResult(autoScalingGroups: [asg])
    }
    1 * mockAgent.loadApp(_)
    1 * mockAgent.loadCluster(credentials, asg, _, region)
    1 * mockAgent.loadServerGroups(credentials, asg, _, region)

    where:
    asg = new AutoScalingGroup()
    asgName = "kato-main-v000"
    region = "us-east-1"
  }

  void "should call the cluster caching agent eviction operation when updating the cluster"() {
    setup:
    def mockAgent = Mock(ClusterCachingAgent)
    InfrastructureCachingAgentFactory.getClusterCachingAgent(credentials, region) >> mockAgent

    when:
    updater.updateCluster(asgName, credentials, region, true)

    then:
    1 * mockAgent.removeServerGroup(credentials, asgName, region)

    where:
    asgName = "kato-main-v000"
    region = "us-west-1"
  }

  void "should retrieve instances from amazon and delegate to the instance caching agent operations"() {
    setup:
    def mockAgent = Mock(InstanceCachingAgent)
    InfrastructureCachingAgentFactory.getInstanceCachingAgent(credentials, region) >> mockAgent
    def asg = new AutoScalingGroup(instances: [new Instance(instanceId: "i-123456")])
    def ec2 = Mock(AmazonEC2)
    amazonClientProvider.getAmazonEC2(credentials, region, true) >> ec2

    when:
    updater.updateInstances(asg, credentials, region)

    then:
    1 * ec2.describeInstances(_) >> { DescribeInstancesRequest request ->
      assert request.instanceIds[0] == asg.instances[0].instanceId
      def reservation = new Reservation(instances: [new com.amazonaws.services.ec2.model.Instance()])
      new DescribeInstancesResult(reservations: [reservation])
    }
    1 * mockAgent.loadNewInstance(credentials, _, region)

    where:
    region = "sa-south-1"
  }

  void "should retrieve launch configuration from amazon and delegate to the launch config caching agent ops"() {
    setup:
    def mockAgent = Mock(LaunchConfigCachingAgent)
    InfrastructureCachingAgentFactory.getLaunchConfigCachingAgent(credentials, region) >> mockAgent
    def launchConfig = new LaunchConfiguration(launchConfigurationName: lcName)
    def autoScaling = Mock(AmazonAutoScaling)
    amazonClientProvider.getAutoScaling(credentials, region, true) >> autoScaling
    def asg = new AutoScalingGroup(launchConfigurationName: lcName)

    when:
    updater.updateLaunchConfig(asg, credentials, region)

    then:
    1 * autoScaling.describeLaunchConfigurations(_) >> { DescribeLaunchConfigurationsRequest req ->
      assert req.launchConfigurationNames.get(0) == lcName
      new DescribeLaunchConfigurationsResult(launchConfigurations: [launchConfig])
    }
    1 * mockAgent.loadNewLaunchConfig(launchConfig, region)

    where:
    lcName = "launchConfig-1234"
    region = "us-west-2"
  }

  void "should retrieve asg image form amazon and delegate to the image caching agent ops"() {
    setup:
    def mockAgent = Mock(ImageCachingAgent)
    InfrastructureCachingAgentFactory.getImageCachingAgent(credentials, region) >> mockAgent
    def ec2 = Mock(AmazonEC2)
    amazonClientProvider.getAmazonEC2(credentials, region, true) >> ec2
    def launchConfig = new LaunchConfiguration(imageId: "ami-deadbeef")
    def image = new Image()

    when:
    updater.updateImage(launchConfig, credentials, region)

    then:
    1 * ec2.describeImages(_) >> { DescribeImagesRequest req ->
      assert req.imageIds[0] == launchConfig.imageId
      new DescribeImagesResult(images: [image])
    }
    1 * mockAgent.loadNewImage(image, region)

    where:
    region = "us-east-1"
  }
}
