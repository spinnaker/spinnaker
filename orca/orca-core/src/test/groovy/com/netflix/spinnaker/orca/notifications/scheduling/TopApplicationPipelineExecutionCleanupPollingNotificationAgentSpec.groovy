/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.notifications.scheduling

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Unroll
import io.reactivex.rxjava3.core.Observable

import java.util.concurrent.atomic.AtomicInteger

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class TopApplicationPipelineExecutionCleanupPollingNotificationAgentSpec extends Specification {
  @Unroll
  void "filter should only consider SUCCEEDED executions"() {
    given:
    def filter = new TopApplicationExecutionCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(ExecutionRepository),
      new NoopRegistry(),
      5000,
      2500,
      []
    ).filter

    expect:
    ExecutionStatus.values().each { s ->
      def pipeline = pipeline {
        stage {
          status = s
        }
        status = s
      }

      filter.test(pipeline) == (s == ExecutionStatus.SUCCEEDED)
    }
  }

  void "mapper should extract id, startTime, status, and pipelineConfigId"() {
    given:
    def pipeline = pipeline {
      id = "ID1"
      pipelineConfigId = "P1"
      startTime = 1000
    }

    and:
    def mapper = new TopApplicationExecutionCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(ExecutionRepository),
      new NoopRegistry(),
      5000,
      2500,
      []
    ).mapper

    expect:
    with(mapper.apply(pipeline)) {
      id == "ID1"
      startTime == 1000
      pipelineConfigId == "P1"
      status == ExecutionStatus.NOT_STARTED
    }
  }

  void "tick should cleanup each application with > threshold # of executions"() {
    given:
    def startTime = new AtomicInteger(0)
    def orchestrations = buildExecutions(startTime, 3)
    def pipelines = buildExecutions(startTime, 3, "P1") + buildExecutions(startTime, 5, "P2")

    def executionRepository = Mock(ExecutionRepository) {
      1 * retrieveAllApplicationNames(_, _) >> ["app1"]
      1 * retrieveOrchestrationsForApplication("app1", _) >> Observable.fromIterable(orchestrations)
    }
    def pipelineDependencyCleanupOperator = Mock(PipelineDependencyCleanupOperator)
    def agent = new TopApplicationExecutionCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      executionRepository,
      new NoopRegistry(),
      5000,
      2,
      [pipelineDependencyCleanupOperator]
    )

    when:
    agent.tick()

    then:
    1 * executionRepository.delete(ORCHESTRATION, orchestrations[0].id)
    1 * pipelineDependencyCleanupOperator.cleanup([orchestrations[0].id])
  }

  private static Collection<PipelineExecutionImpl> buildExecutions(AtomicInteger stageStartTime,
                                                                   int count,
                                                                   String configId = null) {
    (1..count).collect {
      def time = stageStartTime.incrementAndGet()
      pipeline {
        id = time as String
        stage {
          startTime = time
          status = ExecutionStatus.SUCCEEDED
          tasks = [new TaskExecutionImpl()]
        }
        pipelineConfigId = configId
        status = ExecutionStatus.SUCCEEDED
      }
    }
  }
}
