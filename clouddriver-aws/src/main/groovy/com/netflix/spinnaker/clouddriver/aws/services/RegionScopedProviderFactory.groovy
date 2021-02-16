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
package com.netflix.spinnaker.clouddriver.aws.services

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.clouddriver.aws.deploy.AmazonResourceTagger
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProviderAggregator
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders.*
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AWSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.LaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.DefaultLaunchConfigurationBuilder

import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.LocalFileUserDataProperties
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaUtil
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RegionScopedProviderFactory {

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  UserDataProviderAggregator userDataProviderAggregator

  @Autowired
  LocalFileUserDataProperties localFileUserDataProperties

  @Autowired
  AwsConfiguration.DeployDefaults deployDefaults

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired(required = false)
  Collection<AmazonResourceTagger> amazonResourceTaggers

  RegionScopedProvider forRegion(NetflixAmazonCredentials amazonCredentials, String region) {
    new RegionScopedProvider(amazonCredentials, region)
  }

  class RegionScopedProvider {

    final NetflixAmazonCredentials amazonCredentials
    final String region

    RegionScopedProvider(NetflixAmazonCredentials amazonCredentials, String region) {
      this.amazonCredentials = amazonCredentials
      this.region = region
    }

    AmazonEC2 getAmazonEC2() {
      amazonClientProvider.getAmazonEC2(amazonCredentials, region, true)
    }

    AmazonAutoScaling getAutoScaling() {
      amazonClientProvider.getAutoScaling(amazonCredentials, region, true)
    }

    com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(boolean skipEdda) {
      amazonClientProvider.getAmazonElasticLoadBalancingV2(amazonCredentials, region, skipEdda)
    }

    com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing getAmazonElasticLoadBalancing() {
      amazonClientProvider.getAmazonElasticLoadBalancing(amazonCredentials, region, true)
    }

    SubnetAnalyzer getSubnetAnalyzer() {
      SubnetAnalyzer.from(amazonEC2.describeSubnets().subnets)
    }

    SecurityGroupService getSecurityGroupService() {
      new SecurityGroupService(amazonEC2, subnetAnalyzer)
    }

    NetworkInterfaceService getNetworkInterfaceService() {
      new NetworkInterfaceService(securityGroupService, subnetAnalyzer, amazonEC2)
    }

    AsgService getAsgService() {
      new AsgService(getAutoScaling())
    }

    AWSServerGroupNameResolver getAWSServerGroupNameResolver() {
      new AWSServerGroupNameResolver(amazonCredentials.name, region, asgService, clusterProviders)
    }

    AsgReferenceCopier getAsgReferenceCopier(NetflixAmazonCredentials targetCredentials, String targetRegion) {
      new AsgReferenceCopier(amazonClientProvider, amazonCredentials, region, targetCredentials, targetRegion, new IdGenerator())
    }

    AsgLifecycleHookWorker getAsgLifecycleHookWorker() {
      new AsgLifecycleHookWorker(amazonClientProvider, amazonCredentials, region, new IdGenerator())
    }

    LaunchConfigurationBuilder getLaunchConfigurationBuilder() {
      new DefaultLaunchConfigurationBuilder(getAutoScaling(), getAsgService(), getSecurityGroupService(), userDataProviderAggregator, localFileUserDataProperties, deployDefaults)
    }

    LaunchTemplateService getLaunchTemplateService() {
      return new LaunchTemplateService(
        amazonEC2, userDataProviderAggregator, localFileUserDataProperties, amazonResourceTaggers
      )
    }

    AwsConfiguration.DeployDefaults getDeploymentDefaults() {
      return deployDefaults
    }

    Eureka getEureka() {
      if (!amazonCredentials.discoveryEnabled) {
        throw new IllegalStateException('discovery not enabled')
      }
      EurekaUtil.getWritableEureka(amazonCredentials.discovery, region)
    }

    AsgBuilder getAsgBuilderForLaunchConfiguration() {
      new AsgWithLaunchConfigurationBuilder(getLaunchConfigurationBuilder(), getAutoScaling(), getAmazonEC2(), getAsgLifecycleHookWorker())
    }

    AsgBuilder getAsgBuilderForLaunchTemplate() {
      new AsgWithLaunchTemplateBuilder(getLaunchTemplateService(), getSecurityGroupService(), deployDefaults, getAutoScaling(), getAmazonEC2(), getAsgLifecycleHookWorker())
    }
  }
}
