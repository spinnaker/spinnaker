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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.Specification

class TopApplicationExecutionCleanupPollingNotificationAgentSpec extends Specification {
  void "filter should only consider SUCCEEDED executions"() {
    given:
    def filter = new TopApplicationExecutionCleanupPollingNotificationAgent().filter

    expect:
    ExecutionStatus.values().each {
      def stage = new PipelineStage(new Pipeline(), "")
      stage.status = it

      filter.call(new Pipeline(stages: [stage])) == (it == ExecutionStatus.SUCCEEDED)
    }
  }

  void "mapper should extract id and startTime"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "Named Stage")
    stage.startTime = 1000

    def pipeline = new Pipeline(stages: [stage])
    pipeline.id = "ID1"

    and:
    def mapper = new TopApplicationExecutionCleanupPollingNotificationAgent().mapper

    expect:
    mapper.call(pipeline) == [id: "ID1", startTime: 1000]
  }

  void "tick should cleanup each application with > threshold # of executions"() {
    given:
    def orchestrations = buildExecutions(3)
    def pipelines = buildExecutions(3)

    def agent = new TopApplicationExecutionCleanupPollingNotificationAgent(threshold: 2)
    agent.jedisPool = Mock(Pool) {
      1 * getResource() >> {
        return Mock(Jedis) {
          1 * keys("*:app:*") >> { ["orchestration:app:app1", "pipeline:app:app2"] }
          1 * scard("orchestration:app:app1") >> { return orchestrations.size() }
          1 * scard("pipeline:app:app2") >> { return pipelines.size() }
          0 * _
        }
      }
    }
    agent.executionRepository = Mock(ExecutionRepository) {
      1 * retrieveOrchestrationsForApplication("app1") >> { return rx.Observable.from(orchestrations)}
      1 * retrievePipelinesForApplication("app2") >> { return rx.Observable.from(pipelines)}
      0 * _
    }

    when:
    agent.tick()

    then:
    1 * agent.executionRepository.deleteOrchestration(orchestrations[0].id)
    1 * agent.executionRepository.deletePipeline(pipelines[0].id)
  }

  private static Collection<Execution> buildExecutions(int count) {
    (1..count).collect {
      def stage = new PipelineStage(new Pipeline(), "")
      stage.startTime = it
      stage.status = ExecutionStatus.SUCCEEDED
      stage.tasks = [new DefaultTask()]

      def execution = new Pipeline(stages: [stage])
      execution.id = it.toString()

      execution
    }
  }
}
