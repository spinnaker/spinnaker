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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.parallel.PipelineInitializationStage
import com.netflix.spinnaker.orca.pipeline.parallel.PipelineInitializationTask
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionTask
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.job.builder.FlowJobBuilder
import org.springframework.batch.core.job.builder.JobBuilderHelper
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.job.flow.FlowJob
import org.springframework.batch.core.job.flow.support.SimpleFlow
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.batch.core.repository.support.SimpleJobRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineJobBuilderSpec extends Specification {
  @Shared @AutoCleanup("destroy") EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.flushDB()
  }

  Pool<Jedis> jedisPool = new JedisPool("localhost", embeddedRedis.@port)

  @Autowired AbstractApplicationContext applicationContext

  def mapper = new OrcaObjectMapper()
  def executionRepository = new JedisExecutionRepository(jedisPool, 1, 50)

  def pipelineInitializationStage = new PipelineInitializationStage()
  def waitForRequisiteCompletionStage = new WaitForRequisiteCompletionStage()
  def taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])

  @Shared
  def jobBuilder

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton PipelineInitializationStage.PIPELINE_CONFIG_TYPE, pipelineInitializationStage
      registerSingleton WaitForRequisiteCompletionStage.PIPELINE_CONFIG_TYPE, waitForRequisiteCompletionStage
      registerSingleton "waitForRequisiteCompletionTask", new WaitForRequisiteCompletionTask()
      registerSingleton "pipelineInitializationTask", new PipelineInitializationTask()
      registerSingleton("stepExecutionListener", new StepExecutionListenerSupport())
      registerSingleton("defaultExceptionHandler", new DefaultExceptionHandler())
      registerSingleton "taskTaskletAdapter", taskTaskletAdapter
      registerSingleton "stageDetailsTask", new StageDetailsTask()

      autowireBean waitForRequisiteCompletionStage
      autowireBean pipelineInitializationStage
    }

    waitForRequisiteCompletionStage.applicationContext = applicationContext
    pipelineInitializationStage.applicationContext = applicationContext

    def helper = new SimpleJobBuilderHelper("")
    helper.repository(new SimpleJobRepository())

    jobBuilder = new JobFlowBuilder(new FlowJobBuilder(new SimpleJobBuilder(helper)))
  }

  def "should inject 'Initialization' stage for parallel executions"() {
    given:
    def pipeline = new Pipeline()
    pipeline.id = "PIPELINE"
    pipeline.parallel = true
    pipeline.stages << new PipelineStage(pipeline, WaitForRequisiteCompletionStage.PIPELINE_CONFIG_TYPE, [refId: "B"])

    and:
    def pipelineBuilder = Spy(PipelineJobBuilder, constructorArgs: []) {
      1 * buildStart(pipeline) >> { jobBuilder }
    }
    pipelineBuilder.applicationContext = applicationContext
    pipelineBuilder.initialize()

    when:
    FlowJob job = pipelineBuilder.build(pipeline) as FlowJob
    SimpleFlow flow = job.flow as SimpleFlow

    def startState = flow.startState
    def firstTransition = getNextNonStageDetailsTransition(flow, startState.name)
    def nextTransition = getNextNonStageDetailsTransition(flow, firstTransition)

    then:
    pipeline.stages*.refId == ["*", "B"]
    pipeline.stages*.initializationStage == [true, false]
    pipeline.stages*.requisiteStageRefIds == [null, ["*"]] as List

    startState.name.startsWith("Initialization.${pipeline.id}.${pipeline.stages[0].id}")
    nextTransition == "Initialization.${pipeline.id}.ChildExecution.${pipeline.stages[1].refId}.${pipeline.stages[1].id}" as String
  }

  class SimpleJobBuilderHelper extends JobBuilderHelper {
    SimpleJobBuilderHelper(String name) {
      super(name)
    }
  }

  private String getNextNonStageDetailsTransition(flow, currentState){
    String nextName =  flow.transitionMap[currentState][0].next
    while(nextName.contains('stage')){
      nextName = flow.transitionMap[nextName][0].next
    }
    nextName
  }
}
