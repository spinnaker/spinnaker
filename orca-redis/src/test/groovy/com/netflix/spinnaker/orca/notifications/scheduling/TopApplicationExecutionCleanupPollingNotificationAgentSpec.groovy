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
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import redis.clients.jedis.Jedis
import redis.clients.jedis.ScanParams
import redis.clients.jedis.ScanResult
import redis.clients.util.Pool
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class TopApplicationExecutionCleanupPollingNotificationAgentSpec extends Specification {
  @Unroll
  void "filter should only consider SUCCEEDED executions"() {
    given:
    def filter = new TopApplicationExecutionCleanupPollingNotificationAgent().filter

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
    def mapper = new TopApplicationExecutionCleanupPollingNotificationAgent().mapper

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

    def agent = new TopApplicationExecutionCleanupPollingNotificationAgent(threshold: 2)
    agent.jedisPool = Stub(Pool) {
      getResource() >> {
        return Stub(Jedis) {
          scan("0", _ as ScanParams) >> { new ScanResult<String>("0", ["orchestration:app:app1"]) }
          scard("orchestration:app:app1") >> { return orchestrations.size() }
        }
      }
    }
    agent.executionRepository = Mock(ExecutionRepository) {
      retrieveOrchestrationsForApplication("app1", _) >> orchestrations
    }

    when:
    agent.tick()

    then:
    1 * agent.executionRepository.delete(ORCHESTRATION, orchestrations[0].id)
  }

  private
  static Collection<Execution> buildExecutions(AtomicInteger stageStartTime, int count, String configId = null) {
    (1..count).collect {
      def time = stageStartTime.incrementAndGet()
      pipeline {
        id = time as String
        stage {
          startTime = time
          status = ExecutionStatus.SUCCEEDED
          tasks = [new Task()]
        }
        pipelineConfigId = configId
        status = ExecutionStatus.SUCCEEDED
      }
    }
  }
}
