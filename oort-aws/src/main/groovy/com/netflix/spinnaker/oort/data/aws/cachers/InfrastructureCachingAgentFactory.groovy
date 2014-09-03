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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.config.atlas.AtlasHealthApiFactory
import com.netflix.spinnaker.oort.config.discovery.DiscoveryApiFactory
import com.netflix.spinnaker.oort.security.aws.OortNetflixAmazonCredentials
import groovy.transform.CompileStatic

@CompileStatic
class InfrastructureCachingAgentFactory {

  static InfrastructureCachingAgent getImageCachingAgent(NetflixAmazonCredentials account, String region) {
    new ImageCachingAgent(account, region)
  }

  static InfrastructureCachingAgent getClusterCachingAgent(NetflixAmazonCredentials account, String region) {
    new ClusterCachingAgent(account, region)
  }

  static InfrastructureCachingAgent getInstanceCachingAgent(NetflixAmazonCredentials account, String region) {
    new InstanceCachingAgent(account, region)
  }

  static InfrastructureCachingAgent getAtlasHealthCachingAgent(OortNetflixAmazonCredentials account, String region, AtlasHealthApiFactory factory) {
    new AtlasHealthCachingAgent(account, region, factory.createApi(account.atlasHealth, region))
  }

  static InfrastructureCachingAgent getLaunchConfigCachingAgent(NetflixAmazonCredentials account, String region) {
    new LaunchConfigCachingAgent(account, region)
  }

  static InfrastructureCachingAgent getLoadBalancerCachingAgent(NetflixAmazonCredentials account, String region) {
    new LoadBalancerCachingAgent(account, region)
  }

  static InfrastructureCachingAgent getDiscoveryCachingAgent(List<NetflixAmazonCredentials> accounts, String region, DiscoveryApiFactory factory) {
    new DiscoveryCachingAgent(accounts, region, factory.createApi(accounts.head().discovery, region))
  }

}
