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
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import redis.clients.util.Pool
import spock.lang.Specification
import spock.lang.Subject;

class RestorePinnedServerGroupsPollerSpec extends Specification {
  def jedisPool = Mock(Pool)
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)
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

    executionType: Execution.ExecutionType.PIPELINE,
    executionId: "execution-id-1",

    pinnedCapacity: new ServerGroup.Capacity(min: 3, max: 5, desired: 3),
    unpinnedCapacity: new ServerGroup.Capacity(min: 2, max: 5, desired: 3)
  )

  def pinnedServerGroupTag2 = new RestorePinnedServerGroupsPoller.PinnedServerGroupTag(
    id: "entity-tags-id-2",

    cloudProvider: "aws",
    application: "app",
    account: "test",
    location: "us-east-1",
    serverGroup: "app-stack-details-v002",

    executionType: Execution.ExecutionType.PIPELINE,
    executionId: "execution-id-2",

    pinnedCapacity: new ServerGroup.Capacity(min: 3, max: 5, desired: 3),
    unpinnedCapacity: new ServerGroup.Capacity(min: 2, max: 5, desired: 3)
  )

  @Subject
  def restorePinnedServerGroupsAgent = Spy(
    RestorePinnedServerGroupsPoller,
    constructorArgs: [
      jedisPool,
      objectMapper,
      oortService,
      retrySupport,
      registry,
      executionLauncher,
      executionRepository,
      pollerSupport
    ]
  )

  def "should only unpin server group if corresponding execution is complete"() {
    when:
    restorePinnedServerGroupsAgent.poll()

    then:
    1 * restorePinnedServerGroupsAgent.fetchPinnedServerGroupTags() >> {
      return [pinnedServerGroupTag1, pinnedServerGroupTag2]
    }
    1 * restorePinnedServerGroupsAgent.hasCompletedExecution(pinnedServerGroupTag1) >> { return true }
    1 * restorePinnedServerGroupsAgent.hasCompletedExecution(pinnedServerGroupTag2) >> { return false }
    1 * pollerSupport.fetchServerGroup("test", "us-east-1", "app-stack-details-v001") >> {
      return Optional.of(
        new ServerGroup(
          moniker: [app: "app"],
          capacity: new ServerGroup.Capacity(min: 3, max: 5, desired: 3)
        )
      )
    }
    1 * executionLauncher.start(Execution.ExecutionType.ORCHESTRATION, { String configJson ->
      def config = objectMapper.readValue(configJson, Map)
      assert config.stages*.type == ["resizeServerGroup", "deleteEntityTags"]

      return true
    })
  }

  def "should only resize server group if current 'min' capacity matches pinned 'min' capacity"() {
    given:
    true

    when:
    restorePinnedServerGroupsAgent.poll()

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
          capacity: new ServerGroup.Capacity(min: 2, max: 5, desired: 3)
        )
      )
    }
    1 * executionLauncher.start(Execution.ExecutionType.ORCHESTRATION, { String configJson ->
      def config = objectMapper.readValue(configJson, Map)

      // no resize necessary!
      assert config.stages*.type == ["deleteEntityTags"]

      return true
    })
  }
}
