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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.jedis.ScanParams
import redis.clients.jedis.ScanResult
import redis.clients.util.Pool
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class OldPipelineCleanupPollingNotificationAgentSpec extends Specification {

  void 'filter should only consider executions older than threshold'() {
    given:
    def clock = Mock(Clock) {
      millis() >> { Duration.ofDays(3).toMillis() }
    }
    def filter = new OldPipelineCleanupPollingNotificationAgent(clock: clock, thresholdDays: 1).filter

    expect:
    filter.call(new Pipeline(startTime: Duration.ofDays(1).toMillis())) == true
    filter.call(new Pipeline(startTime: Duration.ofDays(3).toMillis())) == false
  }

  void 'mapper should extract id, app, pipelineConfigId, status, startTime and buildTime'() {
    given:
    def pipeline = new Pipeline(id: 'ID1', application: "orca", pipelineConfigId: 'P1', startTime: 1000, buildTime: 1001, status: ExecutionStatus.SUCCEEDED)

    and:
    def mapper = new OldPipelineCleanupPollingNotificationAgent().mapper

    expect:
    with(mapper.call(pipeline)) {
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
    def startTime = new AtomicInteger(0)
    def pipelines = buildPipelines(startTime, 7, "P1")

    and:
    def clock = Mock(Clock) {
      millis() >> { Duration.ofDays(10).toMillis() }
    }
    def executionRepository = Mock(ExecutionRepository) {
      1 * retrievePipelinesForApplication("orca") >> rx.Observable.from(pipelines)
    }
    def jedisPool = Stub(Pool) {
      getResource() >> {
        return Stub(Jedis) {
          scan("0", _ as ScanParams) >> { new ScanResult<String>("0", ["pipeline:app:orca"]) }
        }
      }
    }

    def agent = new OldPipelineCleanupPollingNotificationAgent(
      executionRepository: executionRepository,
      jedisPool: jedisPool,
      clock: clock,
      thresholdDays: 5,
      minimumPipelineExecutions: 3
    )

    when:
    agent.tick()

    then:
    1 * executionRepository.deletePipeline('1')
    1 * executionRepository.deletePipeline('2')
  }

  private static Collection<Pipeline> buildPipelines(AtomicInteger startTime, int count, String pipelineConfigId) {
    (1..count).collect {
      def stage = new Stage<>(new Pipeline(), "whatever")
      stage.startTime = startTime.incrementAndGet()
      stage.status = ExecutionStatus.SUCCEEDED
      stage.tasks = [new Task()]

      def pipeline = new Pipeline(stages: [stage])
      pipeline.id = stage.startTime as String
      pipeline.application = "orca"
      pipeline.pipelineConfigId = pipelineConfigId
      pipeline.startTime = Duration.ofDays(stage.startTime).toMillis()
      pipeline.buildTime = pipeline.startTime
      pipeline.status = ExecutionStatus.SUCCEEDED

      pipeline
    }
  }
}
