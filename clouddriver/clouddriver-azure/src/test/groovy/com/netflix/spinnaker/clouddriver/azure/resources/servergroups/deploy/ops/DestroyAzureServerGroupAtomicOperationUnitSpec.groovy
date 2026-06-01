/*
 * Copyright 2026 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroups.deploy.ops

import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.EnableDisableDestroyAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.DestroyAzureServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandType
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import spock.lang.Specification
import spock.lang.Subject

class DestroyAzureServerGroupAtomicOperationUnitSpec extends Specification {

  static final SERVER_GROUP_NAME = "testazure-web1-d1-v000"
  static final ACCOUNT_NAME = "my-azure-account"
  static final REGION = "westus"
  static final APP_NAME = "testazure"
  static final RESOURCE_GROUP = "${APP_NAME}-${REGION}"

  AzureComputeClient computeClient
  AzureCredentials credentials
  EnableDisableDestroyAzureServerGroupDescription description
  OnDemandCacheUpdater onDemandCacheUpdater

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    computeClient = GroovyMock(AzureComputeClient)
    credentials = GroovyMock(AzureCredentials) {
      asBoolean() >> true
      getComputeClient() >> computeClient
    }

    description = new EnableDisableDestroyAzureServerGroupDescription(
      name: SERVER_GROUP_NAME,
      serverGroupName: SERVER_GROUP_NAME,
      accountName: ACCOUNT_NAME,
      region: REGION,
      application: APP_NAME,
      credentials: credentials
    )

    onDemandCacheUpdater = Mock(OnDemandCacheUpdater) {
      handles(OnDemandType.ServerGroup, AzureCloudProvider.ID) >> true
    }
  }

  void "triggers on-demand cache eviction for destroyed server group"() {
    given:
    def serverGroupDescription = new AzureServerGroupDescription(storageAccountNames: [], enableInboundNAT: false, hasNewSubnet: false)
    computeClient.getServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> serverGroupDescription

    @Subject
    def operation = new DestroyAzureServerGroupAtomicOperation(description, [onDemandCacheUpdater])

    when:
    operation.operate([])

    then:
    1 * computeClient.destroyServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME)
    1 * onDemandCacheUpdater.handle(OnDemandType.ServerGroup, AzureCloudProvider.ID, [
      serverGroupName: SERVER_GROUP_NAME,
      account        : ACCOUNT_NAME,
      region         : REGION
    ])
  }

  void "does not trigger on-demand cache eviction when destroy fails"() {
    given:
    def serverGroupDescription = new AzureServerGroupDescription(storageAccountNames: [], enableInboundNAT: false, hasNewSubnet: false)
    computeClient.getServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> serverGroupDescription
    computeClient.destroyServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> { throw new RuntimeException("Azure API error") }

    @Subject
    def operation = new DestroyAzureServerGroupAtomicOperation(description, [onDemandCacheUpdater])

    when:
    operation.operate([])

    then:
    thrown(AtomicOperationException)
    0 * onDemandCacheUpdater.handle(_, _, _)
  }

  void "works correctly when no on-demand cache updaters are registered"() {
    given:
    def serverGroupDescription = new AzureServerGroupDescription(storageAccountNames: [], enableInboundNAT: false, hasNewSubnet: false)
    computeClient.getServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> serverGroupDescription

    @Subject
    def operation = new DestroyAzureServerGroupAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * computeClient.destroyServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME)
    noExceptionThrown()
  }

  void "does not call updaters that do not handle azure server groups"() {
    given:
    def serverGroupDescription = new AzureServerGroupDescription(storageAccountNames: [], enableInboundNAT: false, hasNewSubnet: false)
    computeClient.getServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> serverGroupDescription

    def nonAzureUpdater = Mock(OnDemandCacheUpdater) {
      handles(OnDemandType.ServerGroup, AzureCloudProvider.ID) >> false
    }

    @Subject
    def operation = new DestroyAzureServerGroupAtomicOperation(description, [nonAzureUpdater, onDemandCacheUpdater])

    when:
    operation.operate([])

    then:
    1 * computeClient.destroyServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME)
    0 * nonAzureUpdater.handle(_, _, _)
    1 * onDemandCacheUpdater.handle(OnDemandType.ServerGroup, AzureCloudProvider.ID, [
      serverGroupName: SERVER_GROUP_NAME,
      account        : ACCOUNT_NAME,
      region         : REGION
    ])
  }

  void "destroy succeeds even when on-demand cache eviction throws"() {
    given:
    def serverGroupDescription = new AzureServerGroupDescription(storageAccountNames: [], enableInboundNAT: false, hasNewSubnet: false)
    computeClient.getServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME) >> serverGroupDescription
    onDemandCacheUpdater.handle(_, _, _) >> { throw new RuntimeException("Azure 429 throttled") }

    @Subject
    def operation = new DestroyAzureServerGroupAtomicOperation(description, [onDemandCacheUpdater])

    when:
    operation.operate([])

    then:
    1 * computeClient.destroyServerGroup(RESOURCE_GROUP, SERVER_GROUP_NAME)
    noExceptionThrown()
  }
}
