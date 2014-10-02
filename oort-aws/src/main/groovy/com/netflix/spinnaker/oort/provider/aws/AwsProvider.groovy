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

package com.netflix.spinnaker.oort.provider.aws

import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.provider.Provider

class AwsProvider implements Provider {
  public static final String PROVIDER_NAME = AwsProvider.name

  public static final String LAUNCH_CONFIG_TYPE = "LaunchConfig"

  private final Collection<CachingAgent> agents

  AwsProvider(Collection<CachingAgent> agents) {
    this.agents = Collections.unmodifiableCollection(agents)
  }

  public static class Identifiers {
    private final String account
    private final String region

    Identifiers(String account, String region) {
      this.account = account
      this.region = region
    }

    String clusterId(String clusterName) {
      "${account}/${clusterName}".toString()
    }

    String serverGroupId(String serverGroupName) {
      "${account}/${region}/${serverGroupName}".toString()
    }

    String loadBalancerId(String loadBalancerName) {
      "${account}/${region}/${loadBalancerName}".toString()
    }

    String launchConfigId(String launchConfigurationName) {
      "${account}/${region}/${launchConfigurationName}".toString()
    }

    String instanceId(String instanceId) {
      "${account}/${region}/${instanceId}".toString()
    }
  }

  @Override
  String getProviderName() {
    return PROVIDER_NAME
  }

  @Override
  Collection<CachingAgent> getCachingAgents() {
    agents
  }
}
