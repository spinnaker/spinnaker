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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.cache.CacheService
import spock.lang.Shared
import spock.lang.Specification

class DefaultClusterLoaderSpec extends Specification {

  @Shared
  DefaultClusterLoader clusterLoader

  def setup() {
    clusterLoader = new DefaultClusterLoader()
  }

  void "cache images through call to aws"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def ec2 = Mock(AmazonEC2)
    clientProvider.getAmazonEC2(_, "us-west-1") >> ec2
    clusterLoader.amazonClientProvider = clientProvider
    def mockImage = Mock(Image)
    mockImage.getImageId() >> "i-123456"

    when:
    clusterLoader.loadImagesCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * ec2.describeImages() >> {
      new DescribeImagesResult().withImages([mockImage])
    }
    1 == clusterLoader.imageCache["us-west-1"].size()
    clusterLoader.imageCache["us-west-1"][mockImage.getImageId()].is(mockImage)

  }

  void "cache launch configurations through call to aws"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def autoScaling = Mock(AmazonAutoScaling)
    clientProvider.getAutoScaling(_, "us-west-1") >> autoScaling
    clusterLoader.amazonClientProvider = clientProvider
    def mockLaunchConfig = Mock(LaunchConfiguration)
    mockLaunchConfig.getLaunchConfigurationName() >> "launchconfig-1234567"

    when:
    clusterLoader.loadLaunchConfigsCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * autoScaling.describeLaunchConfigurations() >> {
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations(mockLaunchConfig)
    }
    1 == clusterLoader.launchConfigCache["us-west-1"].size()
    clusterLoader.launchConfigCache["us-west-1"][mockLaunchConfig.launchConfigurationName].is(mockLaunchConfig)
  }

  void "cache load balancers through call to aws"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def loadBalancing = Mock(AmazonElasticLoadBalancing)
    clientProvider.getAmazonElasticLoadBalancing(_, "us-west-1") >> loadBalancing
    clusterLoader.amazonClientProvider = clientProvider
    def mockLoadBalancer = Mock(LoadBalancerDescription)
    mockLoadBalancer.getLoadBalancerName() >> "app-stack-frontend"

    when:
    clusterLoader.loadLoadBalancersCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * loadBalancing.describeLoadBalancers() >> {
      new DescribeLoadBalancersResult().withLoadBalancerDescriptions([mockLoadBalancer])
    }
    1 == clusterLoader.loadBalancerCache["us-west-1"].size()
    clusterLoader.loadBalancerCache["us-west-1"][mockLoadBalancer.loadBalancerName].is(mockLoadBalancer)
  }

  void "cache clusters through application event"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    account.getName() >> "test"
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "app-stack-v000"
    def cache = Mock(CacheService)
    clusterLoader.clusterCacheService = cache
    def defaults = new OortDefaults()
    clusterLoader.oortDefaults = defaults
    def event = new AmazonDataLoadEvent(new Object(), "us-west-1", account, asg)

    when:
    clusterLoader.onApplicationEvent(event)
    clusterLoader.shutdownAndWait(5)

    then:
    1 * cache.retrieve("test:app-stack") >> {
      null
    }
    1 * cache.put(_, _, _) >> { AmazonCluster cluster ->
      assert cluster.name == "app-stack"
      assert cluster.serverGroups.size()
      assert cluster.serverGroups.first().name == "app-stack-v000"
    }
  }

  void "cache clusters pulls launch config and image info from local cache"() {
    setup:
    def launchConfigName = "launchConfig-1234567"
    def imageId = "i-1234"
    def account = Mock(AmazonNamedAccount)
    account.getName() >> "test"
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "app-stack-v000"
    def cache = Mock(CacheService)
    clusterLoader.clusterCacheService = cache
    def defaults = new OortDefaults()
    clusterLoader.oortDefaults = defaults
    def launchConfig = Mock(LaunchConfiguration)
    launchConfig.getLaunchConfigurationName() >> launchConfigName
    launchConfig.getImageId() >> imageId
    def image = Mock(Image)
    image.getImageId() >> imageId
    clusterLoader.launchConfigCache["us-west-1"] = [(launchConfigName): launchConfig]
    clusterLoader.imageCache["us-west-1"] = [(imageId): image]
    def event = new AmazonDataLoadEvent(new Object(), "us-west-1", account, asg)

    when:
    clusterLoader.onApplicationEvent(event)
    clusterLoader.shutdownAndWait(5)

    then:
    1 * cache.retrieve("test:app-stack") >> {
      null
    }
    1 * cache.put(_, _, _) >> { AmazonCluster cluster ->
      assert cluster.serverGroups.first().launchConfiguration.is(launchConfig)
      assert cluster.serverGroups.first().image.is(image)
    }
  }

  void "cache clusters pulls load balancer info from local cache"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    account.getName() >> "test"
    def asg = Mock(AutoScalingGroup)
    asg.getLoadBalancerNames() >> ["app-stack-frontend"]
    asg.getAutoScalingGroupName() >> "app-stack-v000"
    def cache = Mock(CacheService)
    clusterLoader.clusterCacheService = cache
    def defaults = new OortDefaults()
    clusterLoader.oortDefaults = defaults
    def loadBalancer = Mock(LoadBalancerDescription)
    clusterLoader.loadBalancerCache["us-west-1"] = ["app-stack-frontend": loadBalancer]
    def event = new AmazonDataLoadEvent(new Object(), "us-west-1", account, asg)

    when:
    clusterLoader.onApplicationEvent(event)
    clusterLoader.shutdownAndWait(5)

    then:
    1 * cache.retrieve("test:app-stack") >> {
      null
    }
    1 * cache.put(_, _, _) >> { AmazonCluster cluster ->
      assert cluster.loadBalancers.size()
      assert cluster.loadBalancers.first().name == "app-stack-frontend"
      assert cluster.loadBalancers.first().serverGroups == ["app-stack-v000"] as Set
    }
  }

}
