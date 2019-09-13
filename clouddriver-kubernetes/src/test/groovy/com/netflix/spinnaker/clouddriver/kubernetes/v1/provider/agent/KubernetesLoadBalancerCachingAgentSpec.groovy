/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesLoadBalancerCachingAgentSpec extends Specification {
  static final private String NAMESPACE = "default"
  static final private String ACCOUNT_NAME = "account1"

  KubernetesLoadBalancerCachingAgent cachingAgent
  KubernetesApiAdaptor apiMock
  Registry registryMock
  KubernetesV1Credentials kubernetesCredentials

  def setup() {
    registryMock = Mock(Registry)
    registryMock.get('id') >> 'id'
    registryMock.timer(_) >> null

    apiMock = Mock(KubernetesApiAdaptor)

    apiMock.getNamespacesByName() >> [NAMESPACE]

    def accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)

    kubernetesCredentials = new KubernetesV1Credentials(apiMock, [], [], [], accountCredentialsRepositoryMock)

    def namedCrededentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCrededentialsMock.getCredentials() >> kubernetesCredentials
    namedCrededentialsMock.getName() >> ACCOUNT_NAME

    cachingAgent = new KubernetesLoadBalancerCachingAgent(namedCrededentialsMock, new ObjectMapper(), registryMock, 0, 1)
  }


  @Unroll
  void "correctly reports #type/#provider is handled by the load balancer caching agent (#result)"() {
    expect:
    cachingAgent.handles(type, provider) == result

    where:
    type                                     | provider                   || result
    OnDemandAgent.OnDemandType.LoadBalancer  | KubernetesCloudProvider.ID || true
    OnDemandAgent.OnDemandType.ServerGroup   | KubernetesCloudProvider.ID || false
    OnDemandAgent.OnDemandType.SecurityGroup | KubernetesCloudProvider.ID || false
    OnDemandAgent.OnDemandType.ServerGroup   | "google "                  || false
    OnDemandAgent.OnDemandType.LoadBalancer  | ""                         || false
    null                                     | ""                         || false
  }
}
