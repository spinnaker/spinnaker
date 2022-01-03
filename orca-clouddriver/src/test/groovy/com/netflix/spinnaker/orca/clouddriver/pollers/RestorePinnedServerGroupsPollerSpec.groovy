/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pollers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject

class RestorePinnedServerGroupsPollerSpec extends Specification {
  def notificationClusterLock = Mock(NotificationClusterLock) {
    tryAcquireLock(_, _) >> true
  }
  def objectMapper = new ObjectMapper()
  CloudDriverService cloudDriverService = Mock()
  def executionLauncher = Mock(ExecutionLauncher)
  def executionRepository = Mock(ExecutionRepository)
  def pollerSupport = Mock(PollerSupport)
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }
  def registry = Mock(Registry) {
    createId(_) >> Mock(Id)
    counter(_) >> Mock(Counter)
  }

  def pinnedServerGroupTag1 = new RestorePinnedServerGroupsPoller.PinnedServerGroupTag(
    id: "entity-tags-id-1",

    cloudProvider: "aws",
    application: "app",
    account: "test",
    location: "us-east-1",
    serverGroup: "app-stack-details-v001",

    executionType: ExecutionType.PIPELINE,
    executionId: "execution-id-1",

    pinnedCapacity: ServerGroup.Capacity.builder().min(3).max(5).desired(3).build(),
    unpinnedCapacity: ServerGroup.Capacity.builder().min(2).max(5).desired(3).build()
  )

  def pinnedServerGroupTag2 = new RestorePinnedServerGroupsPoller.PinnedServerGroupTag(
    id: "entity-tags-id-2",

    cloudProvider: "aws",
    application: "app",
    account: "test",
    location: "us-east-1",
    serverGroup: "app-stack-details-v002",

    executionType: ExecutionType.PIPELINE,
    executionId: "execution-id-2",

    pinnedCapacity: ServerGroup.Capacity.builder().min(3).max(5).desired(3).build(),
    unpinnedCapacity: ServerGroup.Capacity.builder().min(2).max(5).desired(3).build()
  )

  @Subject
  def restorePinnedServerGroupsAgent = Spy(
    RestorePinnedServerGroupsPoller,
    constructorArgs: [
      notificationClusterLock,
      objectMapper,
      cloudDriverService,
      retrySupport,
      registry,
      executionLauncher,
      executionRepository,
      "spinnaker",
      pollerSupport
    ]
  )

  def "should only unpin server group if corresponding execution is complete"() {
    when:
    restorePinnedServerGroupsAgent.tick()

    then:
    1 * restorePinnedServerGroupsAgent.fetchPinnedServerGroupTags() >> {
      return [pinnedServerGroupTag1, pinnedServerGroupTag2]
    }
    1 * restorePinnedServerGroupsAgent.hasCompletedExecution(pinnedServerGroupTag1) >> { return true }
    1 * restorePinnedServerGroupsAgent.hasCompletedExecution(pinnedServerGroupTag2) >> { return false }
    1 * pollerSupport.fetchServerGroup("test", "us-east-1", "app-stack-details-v001") >> {
      return Optional.of(
        new ServerGroup(
          moniker: new Moniker(app: "app"),
          capacity: ServerGroup.Capacity.builder().min(3).max(5).desired(3).build()
        )
      )
    }
    1 * executionLauncher.start(ExecutionType.ORCHESTRATION, { Map config ->
      assert config.stages*.type == ["resizeServerGroup", "deleteEntityTags"]
      return true
    })
  }

  def "should only resize server group if current 'min' capacity matches pinned 'min' capacity"() {
    given:
    true

    when:
    restorePinnedServerGroupsAgent.tick()

    then:
    1 * restorePinnedServerGroupsAgent.fetchPinnedServerGroupTags() >> {
      return [pinnedServerGroupTag1]
    }
    1 * restorePinnedServerGroupsAgent.hasCompletedExecution(pinnedServerGroupTag1) >> { return true }
    1 * pollerSupport.fetchServerGroup("test", "us-east-1", "app-stack-details-v001") >> {
      return Optional.of(
        new ServerGroup(
          moniker: [app: "app"],
          // current 'min' capacity no longer matches the pinned 'min' capacity, assume something else resized!
          capacity: ServerGroup.Capacity.builder().min(2).max(5).desired(3).build()
        )
      )
    }
    1 * executionLauncher.start(ExecutionType.ORCHESTRATION, { Map config ->
      // no resize necessary!
      assert config.stages*.type == ["deleteEntityTags"]
      return true
    })
  }
}
