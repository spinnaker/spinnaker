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

package com.netflix.spinnaker.orca.restart

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.SpringBatchConfiguration
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.test.JobCompletionListener
import com.netflix.spinnaker.orca.test.TestConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.spockframework.spring.xml.SpockMockFactoryBean
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.listener.StepExecutionListenerSupport
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@ContextConfiguration(classes = [
  EmbeddedRedisConfiguration, JesqueConfiguration, BatchTestConfiguration,
  SpringBatchConfiguration, OrcaConfiguration, OrcaPersistenceConfiguration,
  JobCompletionListener, TestConfiguration, Config
])
class RollingRestartSpec extends Specification {

  @Autowired PipelineStarter pipelineStarter
  @Autowired ObjectMapper mapper
  @Autowired ExecutionRepository repository
  @Autowired JobCompletionListener jobCompletionListener

  @Autowired StartTask startTask
  @Autowired EndTask endTask

  def "a previously run rolling push pipeline can be restarted and redirects work"() {
    given:
    def pipeline = pipelineStarter.create(mapper.readValue(pipelineConfigFor("test"), Map))
    pipeline.stages[0].tasks << new DefaultTask(id: 2, name: "task1", status: REDIRECT,
      startTime: System.currentTimeMillis(),
      endTime: System.currentTimeMillis(),
      implementingClass: StartTask)
    pipeline.stages[0].tasks << new DefaultTask(id: 3, name: "task2", status: NOT_STARTED,
      startTime: System.currentTimeMillis(),
      implementingClass: EndTask)
    repository.store(pipeline)

    when:
    pipelineStarter.resume(pipeline)
    jobCompletionListener.await()

    then:
    2 * startTask.execute(_) >> new DefaultTaskResult(REDIRECT) >> new DefaultTaskResult(SUCCEEDED)
    1 * endTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

    and:
    repository.retrievePipeline(pipeline.id).status.toString() == SUCCEEDED.name()
  }

  private String pipelineConfigFor(String... stages) {
    def config = [
      application: "app",
      name       : "my-pipeline",
      stages     : stages.collect { [type: it] }
    ]
    mapper.writeValueAsString(config)
  }

  static interface StartTask extends Task {}

  static interface EndTask extends Task {}

  @CompileStatic
  static class RedirectingTestStage extends StageBuilder {
    private final StartTask startTask
    private final EndTask endTask

    RedirectingTestStage(String name, StartTask startTask, EndTask endTask) {
      super(name)
      this.startTask = startTask
      this.endTask = endTask
    }

    @Override
    protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
      def resetListener = new StepExecutionListenerSupport() {
        @Override
        @CompileDynamic
        ExitStatus afterStep(StepExecution stepExecution) {
          if (stepExecution.exitStatus.exitCode == REDIRECT.name()) {
            stage.tasks[0].with {
              status = NOT_STARTED
              endTime = null
            }
          }
          stepExecution.exitStatus
        }
      }
      def start = buildStep(stage, "redirecting", startTask, resetListener)
      def end = buildStep(stage, "final", endTask)
      jobBuilder.next(start)
      jobBuilder.on(REDIRECT.name()).to(start)
      jobBuilder.from(start).on("**").to(end)
    }
  }

  @CompileStatic
  static class Config {
    @Bean
    FactoryBean<StartTask> startTask() {
      new SpockMockFactoryBean<>(StartTask)
    }

    @Bean
    FactoryBean<EndTask> endTask() { new SpockMockFactoryBean<>(EndTask) }

    @Bean
    StageBuilder redirectingTestStage(StartTask startTask, EndTask endTask) {
      new RedirectingTestStage("test", startTask, endTask)
    }
  }
}
