/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.parallel

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDetailsTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.restart.PipelineRestartingSpec
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import groovy.transform.CompileStatic
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@ContextConfiguration(classes=[
  EmbeddedRedisConfiguration,
  JesqueConfiguration,
  TestConfiguration,
  JobCompletionListener,
  BatchTestConfiguration,
  SpringBatchConfiguration,
  OrcaConfiguration,
  OrcaPersistenceConfiguration,
  StageDetailsTask,
  MockStageConfiguration
])
@Ignore
class ParallelCompletionSpec extends Specification {

  @Autowired PipelineStarter pipelineStarter
  @Autowired JobCompletionListener jobCompletionListener
  @Autowired ExecutionRepository repository
  @Autowired TestTask task

  @CompileStatic
  static interface TestTask extends Task {}

  @CompileStatic
  static class MockStageConfiguration {
    @Bean FactoryBean<TestTask> testTask() {
      new SpockMockFactoryBean<>(TestTask)
    }

    @Bean StageDefinitionBuilder testStage() {
      new PipelineRestartingSpec.TestStage()
    }
  }

  @Unroll
  def "pipeline fails when branch #branch fails with status #failureStatus"() {
    given: "one stage will fail but all others (if executed) will succeed"
    task.execute(_) >> { Stage stage ->
      new DefaultTaskResult(stage.name == failAtStage ? failureStatus : SUCCEEDED)
    }

    when: "the pipeline runs"
    def id = pipelineStarter.start(pipelineJson).id
    jobCompletionListener.await()

    then:
    with(repository.retrievePipeline(id)) {
      status == failureStatus
      stages.find { it.name == failAtStage }.status == failureStatus
      stages.findAll { return it.name.startsWith(branchThatShouldGetCanceled) }*.status.contains(CANCELED)
      stages.find { it.name == "B3" }.status == NOT_STARTED
      stages.find { it.name == "AB" }.status == NOT_STARTED
    }

    where:
    failAtStage | branchThatShouldGetCanceled
    "A1"        | "B"
    "B1"        | "A"

    branch = failAtStage.substring(0, 1)
    failureStatus = TERMINAL
  }

  @Shared pipelineDefinition = [
    application    : "idontcare",
    name           : "whatever",
    stages         : [
      [
        refId               : "1",
        requisiteStageRefIds: [],
        type                : "test",
        name                : "A1"
      ],
      [
        refId               : "2",
        requisiteStageRefIds: ["1"],
        type                : "test",
        name                : "A2"
      ],
      [
        refId               : "3",
        requisiteStageRefIds: [],
        type                : "test",
        name                : "B1"
      ],
      [
        refId               : "4",
        requisiteStageRefIds: ["3"],
        type                : "test",
        name                : "B2"
      ],
      [
        refId               : "5",
        requisiteStageRefIds: ["4"],
        type                : "test",
        name                : "B3"
      ],
      [
        refId               : "6",
        requisiteStageRefIds: ["2", "5"],
        type                : "test",
        name                : "AB"
      ]
    ],
    triggers       : [],
    limitConcurrent: false,
    parallel       : true
  ]
  @Shared ObjectMapper mapper = new ObjectMapper()
  @Shared pipelineJson = mapper.writeValueAsString(pipelineDefinition)

}
