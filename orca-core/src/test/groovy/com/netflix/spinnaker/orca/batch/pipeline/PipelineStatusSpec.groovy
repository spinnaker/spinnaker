/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.pipeline

import java.util.concurrent.CountDownLatch
import com.netflix.spinnaker.kork.eureka.EurekaComponents
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

class PipelineStatusSpec extends Specification {

  @AutoCleanup("destroy")
  def applicationContext = new AnnotationConfigApplicationContext()
  @Autowired StepBuilderFactory steps
  @Autowired ExecutionRepository executionRepository
  @Autowired PipelineStarter pipelineStarter

  def latch = new CountDownLatch(1)
  def listener = new JobExecutionListenerSupport() {
    @Override
    void afterJob(JobExecution jobExecution) {
      latch.countDown()
    }
  }

  static mapper = new OrcaObjectMapper()

  def fooTask = Stub(Task) {
    execute(*_) >> DefaultTaskResult.SUCCEEDED
  }

  def setup() {
    applicationContext.with {
      register BatchTestConfiguration,
               EurekaComponents,
               JesqueConfiguration,
               EmbeddedRedisConfiguration,
               OrcaConfiguration
      beanFactory.registerSingleton "testJobListener", listener
      def stages = ["foo", "bar", "baz"].collect { name ->
        new TestStage(name, steps, executionRepository, fooTask)
      }
      stages.each {
        beanFactory.registerSingleton "${it.type}Stage", it
      }

      refresh()
      stages.each {
        it.applicationContext = applicationContext
        beanFactory.autowireBean(it)
      }
      beanFactory.autowireBean this
    }
  }

  def "can get a list of stages from the pipeline"() {
    expect:
    with(pipelineStarter.start(configJson)) {
      stages.size() == 3
      stages.type == stageTypes
    }

    where:
    stageTypes = ["foo", "bar", "baz"]
    config = [
      application: "app",
      name       : "test-pipeline",
      stages     : stageTypes.collect {
        [type: it]
      }
    ]
    configJson = mapper.writeValueAsString(config)
  }

  def "can get the status of each stage"() {
    given:
    def pipeline = pipelineStarter.start(configJson)
    latch.await()

    expect:
    with(executionRepository.retrievePipeline(pipeline.id)) {
      stages*.status == [ExecutionStatus.SUCCEEDED] * 3
    }

    where:
    stageTypes = ["foo", "bar", "baz"]
    config = [
      application: "app",
      name       : "test-pipeline",
      stages     : stageTypes.collect {
        [type: it]
      }
    ]
    configJson = mapper.writeValueAsString(config)
  }

}
