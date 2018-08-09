/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesServerGroupCachingAgentSpec extends Specification {
  static final private String NAMESPACE = "default"
  static final private String ACCOUNT_NAME = "account1"
  static final private String APP = "app"
  static final private String CLUSTER = "$APP-cluster"
  static final private String REPLICATION_CONTROLLER = "$CLUSTER-v000"
  static final private String POD = "$REPLICATION_CONTROLLER-instance"

  KubernetesServerGroupCachingAgent cachingAgent
  ReplicationControllerList replicationControllerList
  PodList podList
  KubernetesApiAdaptor apiMock

  Registry registryMock
  KubernetesV1Credentials kubernetesCredentials

  String applicationKey
  String clusterKey
  String serverGroupKey
  String instanceKey

  def setup() {
    registryMock = Mock(Registry)
    registryMock.get('id') >>  'id'
    registryMock.timer(_) >> null

    replicationControllerList = Mock(ReplicationControllerList)
    podList = Mock(PodList)
    apiMock = Mock(KubernetesApiAdaptor)

    def accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)

    kubernetesCredentials = new KubernetesV1Credentials(apiMock, [], [], [], accountCredentialsRepositoryMock)

    def namedCrededentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCrededentialsMock.getCredentials() >> kubernetesCredentials
    namedCrededentialsMock.getName() >> ACCOUNT_NAME

    applicationKey = Keys.getApplicationKey(APP)
    clusterKey = Keys.getClusterKey(ACCOUNT_NAME, APP, 'serverGroup', CLUSTER)
    serverGroupKey = Keys.getServerGroupKey(ACCOUNT_NAME, NAMESPACE, REPLICATION_CONTROLLER)
    instanceKey = Keys.getInstanceKey(ACCOUNT_NAME, NAMESPACE, POD)

    cachingAgent = new KubernetesServerGroupCachingAgent(namedCrededentialsMock, new ObjectMapper(), registryMock, 0, 1)
  }

  void "Should store a single replication controller object and relationships"() {
    setup:
      def replicationControllerMock = Mock(ReplicationController)
      def replicationControllerMetadataMock = Mock(ObjectMeta)
      def replicationControllerSpecMock = Mock(ReplicationControllerSpec)
      def selector = ['replicationController': REPLICATION_CONTROLLER.toString()]
      replicationControllerSpecMock.getSelector() >> selector
      replicationControllerMetadataMock.getName() >> REPLICATION_CONTROLLER
      replicationControllerMetadataMock.getNamespace() >> NAMESPACE
      replicationControllerMock.getMetadata() >> replicationControllerMetadataMock
      replicationControllerMock.getSpec() >> replicationControllerSpecMock

      def podMock = Mock(ReplicationController)
      def podMetadataMock = Mock(ObjectMeta)
      podMetadataMock.getLabels() >> selector
      podMetadataMock.getName() >> POD
      podMetadataMock.getNamespace() >> NAMESPACE
      podMock.getMetadata() >> podMetadataMock
      apiMock.getReplicationControllers(NAMESPACE) >> [replicationControllerMock]
      apiMock.getEvents(NAMESPACE, KubernetesUtil.DEPRECATED_SERVER_GROUP_KIND) >> [:].withDefault { _ -> [] }
      apiMock.getAutoscalers(NAMESPACE, KubernetesUtil.DEPRECATED_SERVER_GROUP_KIND) >> [:]
      apiMock.getPods(NAMESPACE) >> [podMock]
      apiMock.getNamespacesByName() >> [NAMESPACE]

      def providerCacheMock = Mock(ProviderCache)
      providerCacheMock.getAll(_, _) >> []

    when:
      def result = cachingAgent.loadData(providerCacheMock)

    then:
      result.cacheResults.applications.attributes.name == [APP]
      result.cacheResults.applications.relationships.serverGroups[0][0] == serverGroupKey
      result.cacheResults.applications.relationships.clusters[0][0] == clusterKey

      result.cacheResults.clusters.attributes.name == [CLUSTER]
      result.cacheResults.clusters.relationships.serverGroups[0][0] == serverGroupKey
      result.cacheResults.clusters.relationships.applications[0][0] == applicationKey

      result.cacheResults.serverGroups.attributes.name == [REPLICATION_CONTROLLER]
      result.cacheResults.serverGroups.relationships.clusters[0][0] == clusterKey
      result.cacheResults.serverGroups.relationships.applications[0][0] == applicationKey
      result.cacheResults.serverGroups.relationships.instances[0][0] == instanceKey

      result.cacheResults.instances.relationships.clusters[0][0] == clusterKey
      result.cacheResults.instances.relationships.applications[0][0] == applicationKey
      result.cacheResults.instances.relationships.serverGroups[0][0] == serverGroupKey
  }

  @Unroll
  void "correctly reports #type/#provider is handled by the server group caching agent (#result)"() {
    expect:
    cachingAgent.handles(type, provider) == result

    where:
    type                                     | provider                   || result
    OnDemandAgent.OnDemandType.ServerGroup   | KubernetesCloudProvider.ID || true
    OnDemandAgent.OnDemandType.LoadBalancer  | KubernetesCloudProvider.ID || false
    OnDemandAgent.OnDemandType.SecurityGroup | KubernetesCloudProvider.ID || false
    OnDemandAgent.OnDemandType.ServerGroup   | "google "                  || false
    OnDemandAgent.OnDemandType.LoadBalancer  | ""                         || false
    null                                     | ""                         || false
  }
}
