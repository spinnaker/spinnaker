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
package com.netflix.spinnaker.orca.notifications.scheduling

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import io.reactivex.rxjava3.core.Observable

import java.time.Clock
import java.time.Duration
import java.time.Instant

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class OldPipelineCleanupPollingNotificationAgentSpec extends Specification {

  void 'filter should only consider executions older than threshold'() {
    given:
    def clock = Mock(Clock) {
      millis() >> { Duration.ofDays(3).toMillis() }
    }
    def filter = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(ExecutionRepository),
      clock,
      new NoopRegistry(),
      5000,
      1,
      5,
      []
    ).filter

    expect:
    filter.test(pipeline {
      status = ExecutionStatus.SUCCEEDED
      startTime = Duration.ofDays(1).toMillis()
    }) == true
    filter.test(pipeline {
      status = ExecutionStatus.RUNNING
      startTime = Duration.ofDays(1).toMillis()
    }) == false
    filter.test(pipeline {
      status = ExecutionStatus.SUCCEEDED
      startTime = Duration.ofDays(3).toMillis()
    }) == false
  }

  void 'mapper should extract id, app, pipelineConfigId, status, startTime and buildTime'() {
    given:
    def pipeline = pipeline {
      id = 'ID1'
      application = "orca"
      pipelineConfigId = 'P1'
      startTime = 1000
      buildTime = 1001
      status = ExecutionStatus.SUCCEEDED
    }

    and:
    def mapper = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      Mock(ExecutionRepository),
      Mock(Clock),
      new NoopRegistry(),
      5000,
      1,
      5,
      []
    ).mapper

    expect:
    with(mapper.apply(pipeline)) {
      id == "ID1"
      application == "orca"
      pipelineConfigId == "P1"
      startTime == 1000
      buildTime == 1001
      status == ExecutionStatus.SUCCEEDED
    }
  }

  void 'tick should cleanup pipeline with executions older than threshold, but no less than minimum execution limit'() {
    given:
    def startTimes = [
            Instant.parse("2019-06-01T00:00:00Z"),
            Instant.parse("2019-06-01T01:00:00Z"),
            Instant.parse("2019-06-01T02:00:00Z"),
            Instant.parse("2019-06-01T03:00:00Z"),
            Instant.parse("2019-06-01T04:00:00Z"),
            Instant.parse("2019-06-01T05:00:00Z"),
            Instant.parse("2019-06-01T06:00:00Z"),
    ]

    def pipelines = buildPipelines(startTimes, "P1")
    def thresholdDays = 5
    def retain = 3

    and:
    def clock = Mock(Clock) {
      // `thresholdDays` days and one minute after pipeline4 above, so that the first five pipelines
      // are past the threshold
      millis() >> { Instant.parse("2019-06-06T04:01:00Z").toEpochMilli() }
    }
    def executionRepository = Mock(ExecutionRepository) {
      1 * retrieveAllApplicationNames(PIPELINE) >> ["orca"]
      1 * retrievePipelinesForApplication("orca") >> Observable.fromIterable(pipelines)
    }
    def pipelineDependencyCleanupOperator = Mock(PipelineDependencyCleanupOperator)
    def agent = new OldPipelineCleanupPollingNotificationAgent(
      Mock(NotificationClusterLock),
      executionRepository,
      clock,
      new NoopRegistry(),
      5000,
      thresholdDays,
      retain,
      [pipelineDependencyCleanupOperator]
    )

    when:
    agent.tick()

    then:
    // with pipeline executions at D1, D2, D3, D4, D5, D6, D7, and clock at D10, we
    // expect D1-5 to be too old, but for the most recent 3 to be retained
    1 * executionRepository.delete(PIPELINE, '1')
    1 * executionRepository.delete(PIPELINE, '2')
    1 * pipelineDependencyCleanupOperator.cleanup(['1', '2'])
  }

  private
  static Collection<PipelineExecutionImpl> buildPipelines(List<Instant> startTimes, String configId) {
    (1..startTimes.size()).collect {
      def n = it
      def time = startTimes.get(n - 1).toEpochMilli()
      pipeline {
        id = n
        application = "orca"
        pipelineConfigId = configId
        startTime = time
        buildTime = time
        status = ExecutionStatus.SUCCEEDED
        stage {
          type = "whatever"
          startTime = time
          status = ExecutionStatus.SUCCEEDED
          tasks = [new TaskExecutionImpl()]
        }
      }
    }
  }
}
