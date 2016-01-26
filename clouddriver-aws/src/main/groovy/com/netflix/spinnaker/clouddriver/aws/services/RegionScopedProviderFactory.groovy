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
import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.deploy.AWSServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.AsgReferenceCopier
import com.netflix.spinnaker.clouddriver.aws.deploy.DefaultLaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.LaunchConfigurationBuilder
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.Eureka
import com.netflix.spinnaker.clouddriver.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.model.SubnetAnalyzer
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.apache.http.impl.client.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.client.ApacheClient

import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

@Component
class RegionScopedProviderFactory {

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  List<UserDataProvider> userDataProviders

  @Autowired
  AwsConfiguration.DeployDefaults deployDefaults

  @Autowired
  List<ClusterProvider> clusterProviders

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

    LaunchConfigurationBuilder getLaunchConfigurationBuilder() {
      new DefaultLaunchConfigurationBuilder(getAutoScaling(), getAsgService(), getSecurityGroupService(),
        userDataProviders, deployDefaults)
    }

    Eureka getEureka() {
      if (!amazonCredentials.discoveryEnabled) {
        throw new IllegalStateException('discovery not enabled')
      }
      String endpoint = amazonCredentials.discovery.replaceAll(Pattern.quote('{{region}}'), region)
      new RestAdapter.Builder().setEndpoint(endpoint).setClient(getApacheClient()).build().create(Eureka)
    }
  }

  //Lazy-create apache client on request if there is a discoveryEnabled AmazonCredentials:
  private final AtomicReference<ApacheClient> apacheClient = new AtomicReference<>(null)
  private ApacheClient getApacheClient() {
    if (apacheClient.get() == null) {
      synchronized (apacheClient) {
        if (apacheClient.get() == null) {
          apacheClient.set(new ApacheClient(HttpClients.createDefault()))
        }
      }
    }
    return apacheClient.get()
  }

}
