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
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.data.aws.cachers.ImageCacher
import com.netflix.spinnaker.oort.data.aws.cachers.InstanceCacher
import com.netflix.spinnaker.oort.data.aws.cachers.LaunchConfigCacher
import com.netflix.spinnaker.oort.data.aws.cachers.LoadBalancerCacher
import com.netflix.spinnaker.oort.data.aws.computers.DefaultClusterLoader
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import org.apache.directmemory.cache.CacheService
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Executors

class DefaultClusterLoaderSpec extends Specification {

  @Shared
  DefaultClusterLoader clusterLoader

  def setup() {
    clusterLoader = new DefaultClusterLoader()
    clusterLoader.executorService = Executors.newSingleThreadExecutor()
  }

  void "cache images through call to aws"() {
    setup:
    def imageCacher = new ImageCacher()
    def cache = Mock(CacheService)
    imageCacher.cacheService = cache
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def ec2 = Mock(AmazonEC2)
    clientProvider.getAmazonEC2(_, "us-west-1") >> ec2
    imageCacher.amazonClientProvider = clientProvider
    def mockImage = Mock(Image)
    mockImage.getImageId() >> "i-123456"

    when:
    imageCacher.loadImagesCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * ec2.describeImages() >> {
      new DescribeImagesResult().withImages([mockImage])
    }
    1 * cache.put(_, _, _)
  }

  void "cache launch configurations through call to aws"() {
    setup:
    def launchConfigCacher = new LaunchConfigCacher()
    def cache = Mock(CacheService)
    launchConfigCacher.cacheService = cache
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def autoScaling = Mock(AmazonAutoScaling)
    clientProvider.getAutoScaling(_, "us-west-1") >> autoScaling
    launchConfigCacher.amazonClientProvider = clientProvider
    def mockLaunchConfig = Mock(LaunchConfiguration)
    mockLaunchConfig.getLaunchConfigurationName() >> "launchconfig-1234567"

    when:
    launchConfigCacher.loadLaunchConfigsCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * autoScaling.describeLaunchConfigurations() >> {
      new DescribeLaunchConfigurationsResult().withLaunchConfigurations(mockLaunchConfig)
    }
    1 * cache.put(_, _, _)
  }

  void "cache load balancers through call to aws"() {
    setup:
    def loadBalancerCacher = new LoadBalancerCacher()
    def cache = Mock(CacheService)
    loadBalancerCacher.cacheService = cache
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def loadBalancing = Mock(AmazonElasticLoadBalancing)
    clientProvider.getAmazonElasticLoadBalancing(_, "us-west-1") >> loadBalancing
    loadBalancerCacher.amazonClientProvider = clientProvider
    def mockLoadBalancer = Mock(LoadBalancerDescription)
    mockLoadBalancer.getLoadBalancerName() >> "app-stack-frontend"

    when:
    loadBalancerCacher.loadLoadBalancersCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * loadBalancing.describeLoadBalancers() >> {
      new DescribeLoadBalancersResult().withLoadBalancerDescriptions([mockLoadBalancer])
    }
    1 * cache.put(_, _, _)
  }

  void "cache instances through call to aws"() {
    setup:
    def instanceCacher = new InstanceCacher()
    def cache = Mock(CacheService)
    instanceCacher.cacheService = cache
    def account = Mock(AmazonNamedAccount)
    def clientProvider = Mock(AmazonClientProvider)
    def ec2 = Mock(AmazonEC2)
    clientProvider.getAmazonEC2(_, "us-west-1") >> ec2
    instanceCacher.amazonClientProvider = clientProvider
    def mockInstance = Mock(Instance)
    mockInstance.getInstanceId() >> "i-12345"

    when:
    instanceCacher.loadInstancesCallable(account, "us-west-1")

    then:
    1 * account.getCredentials() >> {
      new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
    }
    1 * ec2.describeInstances() >> {
      new DescribeInstancesResult().withReservations(new Reservation().withInstances([mockInstance]))
    }
    1 * cache.put(_, _, _)
  }

  void "cache clusters through application event"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    account.getName() >> "test"
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "app-stack-v000"
    def cache = Mock(CacheService)
    clusterLoader.cacheService = cache
    def defaults = new OortDefaults()
    clusterLoader.oortDefaults = defaults
    def event = new AmazonDataLoadEvent(new Object(), "us-west-1", account, asg)

    when:
    clusterLoader.onApplicationEvent(event)
    clusterLoader.shutdownAndWait(5)

    then:
    1 * cache.retrieve(Keys.getClusterKey("app-stack", "app", "test")) >> {
      null
    }
    1 * cache.put(_, _) >> { AmazonCluster cluster ->
      assert cluster.name == "app-stack"
      assert cluster.serverGroups.size()
      assert cluster.serverGroups.first().name == "app-stack-v000"
    }
  }

}
