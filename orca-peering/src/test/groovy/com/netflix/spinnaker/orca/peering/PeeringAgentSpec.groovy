/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class PeeringAgentSpec extends Specification {
  MySqlRawAccess src = Mock(MySqlRawAccess)
  MySqlRawAccess dest = Mock(MySqlRawAccess)
  PeeringMetrics metrics = Mock(PeeringMetrics)
  ExecutionCopier copier = Mock(ExecutionCopier)
  def clockDrift = 10

  PeeringAgent constructPeeringAgent(DynamicConfigService dynamicConfigService = DynamicConfigService.NOOP) {
    return new PeeringAgent(
        "peeredId",
        1000,
        clockDrift,
        src,
        dest,
        dynamicConfigService,
        metrics,
        copier,
        Mock(NotificationClusterLock)
    )
  }

  def "respects dynamic enabled prop"() {
    given:
    def dynamicConfigService = Mock(DynamicConfigService)
    def peeringAgent = constructPeeringAgent(dynamicConfigService)

    when: 'disabled globally'
    peeringAgent.tick()

    then:
    1 * dynamicConfigService.isEnabled("pollers.peering", true) >> {
      return false
    }
    0 * dynamicConfigService.isEnabled("pollers.peering.peeredId", true) >> {
      return false
    }
    0 * src.getCompletedExecutionIds(_, _, _)
    0 * dest.getCompletedExecutionIds(_, _, _)

    when: 'disabled for a given agent only'
    peeringAgent.tick()

    then:
    1 * dynamicConfigService.isEnabled("pollers.peering", true) >> {
      return true
    }
    1 * dynamicConfigService.isEnabled("pollers.peering.peeredId", true) >> {
      return false
    }
    0 * src.getCompletedExecutionIds(_, _, _)
    0 * dest.getCompletedExecutionIds(_, _, _)
  }

  @Unroll
  def "correctly computes the execution diff for completed #executionType"() {
    def peeringAgent = constructPeeringAgent()
    peeringAgent.completedPipelinesMostRecentUpdatedTime = 1
    peeringAgent.completedOrchestrationsMostRecentUpdatedTime = 2

    def copyCallCount = (int)Math.signum(toCopy.size())
    def correctMax = Math.max(0, ((srcKeys + srcKeysNull).max { it.updated_at }?.updated_at ?: 2) - clockDrift)

    when:
    peeringAgent.peerCompletedExecutions(executionType)

    then:
    1 * src.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> srcKeys
    1 * src.getCompletedExecutionIds(executionType, null, mostRecentTimeStamp) >> srcKeysNull
    1 * dest.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> destKeys

    if (toDelete.isEmpty()) {
      0 * dest.deleteExecutions(executionType, _)
      0 * metrics.incrementNumDeleted(executionType, _)
    } else {
      1 * dest.deleteExecutions(executionType, toDelete)
      1 * metrics.incrementNumDeleted(executionType, toDelete.size())
    }

    copyCallCount * copier.copyInParallel(executionType, toCopy, ExecutionState.COMPLETED) >>
        new ExecutionCopier.MigrationChunkResult(
            (srcKeys + srcKeysNull).findAll {toCopy.contains(it.id)}.collect {it.updated_at}.max() ?: 0,
            toCopy.size(),
            false)

    if (executionType == PIPELINE) {
      assert peeringAgent.completedPipelinesMostRecentUpdatedTime == correctMax
      assert peeringAgent.completedOrchestrationsMostRecentUpdatedTime == 2
    } else {
      assert peeringAgent.completedPipelinesMostRecentUpdatedTime == 1
      assert peeringAgent.completedOrchestrationsMostRecentUpdatedTime == correctMax
    }

    where:
    // Note: since the logic for executions and orchestrations should be the same, it's overkill to have the same set of tests for each
    // but it's easy so why not?
    executionType | mostRecentTimeStamp | srcKeys          | srcKeysNull                      | destKeys                                         || toDelete              | toCopy
    PIPELINE      | 1                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 10), key("ID4", 10)] || ["ID4"]               | ["ID2", "ID3"]
    PIPELINE      | 1                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || []                    | []
    PIPELINE      | 1                   | []               | []                               | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || ["ID1", "ID2", "ID3"] | []
    PIPELINE      | 1                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | []                                               || []                    | ["ID1", "ID2", "ID3"]
    PIPELINE      | 1                   | []               | [key("ID2", 20)]                 | []                                               || []                    | ["ID2"]
    PIPELINE      | 1                   | [key("ID1", 10)] | []                               | []                                               || []                    | ["ID1"]

    ORCHESTRATION | 2                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 10), key("ID4", 10)] || ["ID4"]               | ["ID2", "ID3"]
    ORCHESTRATION | 2                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || []                    | []
    ORCHESTRATION | 2                   | []               | []                               | [key("ID1", 10), key("ID2", 20), key("ID3", 30)] || ["ID1", "ID2", "ID3"] | []
    ORCHESTRATION | 2                   | [key("ID1", 10)] | [key("ID2", 20), key("ID3", 30)] | []                                               || []                    | ["ID1", "ID2", "ID3"]
    ORCHESTRATION | 2                   | []               | [key("ID2", 20)]                 | []                                               || []                    | ["ID2"]
    ORCHESTRATION | 2                   | [key("ID1", 10)] | []                               | []                                               || []                    | ["ID1"]
  }

  @Unroll
  def "updates the most recent timestamp even when there is nothing to copy"() {
    def peeringAgent = constructPeeringAgent()

    def correctMax = Math.max(0, (srcKeys.max { it.updated_at }?.updated_at ?: 0) - clockDrift)

    when:
    peeringAgent.peerExecutions(executionType)

    then:
    1 * src.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> srcKeys
    1 * src.getCompletedExecutionIds(executionType, null, mostRecentTimeStamp) >> []
    1 * dest.getCompletedExecutionIds(executionType, "peeredId", mostRecentTimeStamp) >> destKeys

    if (toDelete.isEmpty()) {
      0 * dest.deleteExecutions(executionType, _)
      0 * metrics.incrementNumDeleted(executionType, _)
    } else {
      1 * dest.deleteExecutions(executionType, toDelete)
      1 * metrics.incrementNumDeleted(executionType, toDelete.size())
    }

    if (toCopy.isEmpty()) {
      0 * copier.copyInParallel(executionType, _, _)
    } else {
      1 * copier.copyInParallel(executionType, toCopy, ExecutionState.COMPLETED) >>
          new ExecutionCopier.MigrationChunkResult(0, 0, false)
    }

    if (executionType == PIPELINE) {
      assert peeringAgent.completedPipelinesMostRecentUpdatedTime == correctMax
      assert peeringAgent.completedOrchestrationsMostRecentUpdatedTime == 0
    } else {
      assert peeringAgent.completedPipelinesMostRecentUpdatedTime == 0
      assert peeringAgent.completedOrchestrationsMostRecentUpdatedTime == correctMax
    }

    where:
    // Note: since the logic for executions and orchestrations should be the same, it's overkill to have the same set of tests for each
    // but it's easy so why not?
    executionType | mostRecentTimeStamp | srcKeys           | destKeys                           || toDelete | toCopy
    PIPELINE      | 0                   | []                | []                                 || []       | []
    PIPELINE      | 0                   | []                | [key("ID1", 100)]                  || ["ID1"]  | []
    PIPELINE      | 0                   | [key("ID1", 100)] | [key("ID1", 100)]                  || []       | []
    PIPELINE      | 0                   | [key("ID1", 100)] | [key("ID1", 100), key("ID2", 200)] || ["ID2"]  | []
  }

  @Unroll
  def "copies all running executions of #executionType"() {
    given:
    def peeringAgent = constructPeeringAgent()

    when:
    peeringAgent.peerActiveExecutions(executionType)

    then:
    1 * src.getActiveExecutionIds(executionType, "peeredId") >> activeIds
    1 * src.getActiveExecutionIds(executionType, null) >> activeIdsNull

    if (copyCallCount == 0) {
      copyCallCount * copier.copyInParallel(executionType, _, _)
    } else {
      copyCallCount * copier.copyInParallel(executionType, activeIds + activeIdsNull, ExecutionState.ACTIVE) >>
          new ExecutionCopier.MigrationChunkResult(
              30,
              activeIdsNull.size() + activeIds.size(),
              false)
    }

    where:
    executionType | activeIds       | activeIdsNull | copyCallCount
    PIPELINE      | []              | []            | 0
    PIPELINE      | ["ID1"]         | []            | 1
    PIPELINE      | ["ID1", "ID4"]  | ["ID5"]       | 1
    ORCHESTRATION | []              | []            | 0
    ORCHESTRATION | ["ID1"]         | []            | 1
    ORCHESTRATION | ["ID1", "ID4"]  | ["ID5"]       | 1
  }

  @Unroll
  def "doesn't delete the world"() {
    given:
    def dynamicConfigService = Mock(DynamicConfigService)
    def peeringAgent = constructPeeringAgent(dynamicConfigService)
    peeringAgent.completedPipelinesMostRecentUpdatedTime = 1
    peeringAgent.completedOrchestrationsMostRecentUpdatedTime = 1
    dynamicConfigService.isEnabled(_, _) >> { return true }
    dynamicConfigService.getConfig(Integer.class, "pollers.peering.max-allowed-delete-count", 100) >> { return 1 }

    def copyCallCount = (int)Math.signum(toCopy.size())
    def deleteCallCount = toDelete.size() > 0 ? 1 : 0
    def deleteFailureCount = toDelete.size() > 0 ? 0 : 1

    when:
    peeringAgent.peerCompletedExecutions(executionType)

    then:
    1 * src.getCompletedExecutionIds(executionType, "peeredId", 1) >> srcKeys
    1 * src.getCompletedExecutionIds(executionType, null, 1) >> []
    1 * dest.getCompletedExecutionIds(executionType, "peeredId", 1) >> destKeys

    if (toDelete.isEmpty()) {
      0 * dest.deleteExecutions(executionType, _)
      0 * metrics.incrementNumDeleted(executionType, _)
    } else {
      1 * dest.deleteExecutions(executionType, toDelete)
      1 * metrics.incrementNumDeleted(executionType, toDelete.size())
    }
    deleteFailureCount * metrics.incrementNumErrors(executionType)

    copyCallCount * copier.copyInParallel(executionType, toCopy, ExecutionState.COMPLETED) >>
        new ExecutionCopier.MigrationChunkResult(30, 1, false)

    where:
    // Note: since the logic for executions and orchestrations should be the same, it's overkill to have the same set of tests for each
    // but it's easy so why not?
    executionType | srcKeys          | destKeys                                         || toDelete | toCopy
    PIPELINE      | [key("ID3", 30)] | [key("IDx", 10), key("IDy", 10), key("ID3", 10)] || []       | ["ID3"]
    PIPELINE      | [key("ID3", 30)] | [key("IDx", 10), key("ID3", 10)]                 || ["IDx"]  | ["ID3"]

    ORCHESTRATION | [key("ID3", 30)] | [key("IDx", 10), key("IDy", 10), key("ID3", 10)] || []       | ["ID3"]
    ORCHESTRATION | [key("ID3", 30)] | [key("IDx", 10), key("ID3", 10)]                 || ["IDx"]  | ["ID3"]
  }

  private static def key(id, updated_at) {
    return new SqlRawAccess.ExecutionDiffKey(id, updated_at)
  }
}
