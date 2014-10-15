/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import javax.annotation.PostConstruct
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.StageBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class PipelineStarter {

  @Autowired private ApplicationContext applicationContext
  @Autowired private JobLauncher launcher
  @Autowired private JobBuilderFactory jobs
  @Autowired private StepBuilderFactory steps
  @Autowired private ObjectMapper mapper

  private final Map<String, StageBuilder> stages = [:]

  /**
   * Builds and launches a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  Pipeline start(String configJson) {
    def config = parseConfig(configJson)
    def stageBuilders = stageBuildersFor(config)
    def job = createJobFrom(stageBuilders, config)
    def jobExecution = launcher.run(job, new JobParameters())
    def stages = stageBuilders.collect {
      new Stage(it.name)
    }
    new Pipeline(jobExecution.id.toString(), stages)
  }

  @PostConstruct
  void initialize() {
    applicationContext.getBeansOfType(StageBuilder).values().each {
      stages[it.name] = it
    }
    applicationContext.getBeansOfType(StandaloneTask).values().each {
      def stage = new SimpleStage(it.name, it)
      applicationContext.autowireCapableBeanFactory.autowireBean(stage)
      // TODO: this should be a prototype scoped bean or use a factory I guess
      stages[it.name] = stage
    }
  }

  private List<Map<String, ?>> parseConfig(String configJson) {
    mapper.readValue(configJson, new TypeReference<List<Map>>() {}) as List
  }

  private List<StageBuilder> stageBuildersFor(List<Map<String, ?>> config) {
    config.collect {
      if (it.providerType == "gce") {
        it.type = "${it.type}_gce"
      }

      if (stages.containsKey(it.type)) {
        stages.get(it.type)
      } else {
        throw new NoSuchStageException(it.type as String)
      }
    }
  }

  private Job createJobFrom(List<StageBuilder> stageBuilders, List<Map<String, ?>> config) {
    // TODO: can we get any kind of meaningful identifier from the mayo config?
    def jobBuilder = jobs.get("orca-job-${UUID.randomUUID()}")
                         .flow(configStep(config))
    def flow = (JobFlowBuilder) stageBuilders.inject(jobBuilder, this.&stageFromConfig)
    flow.build().build()
  }

  private TaskletStep configStep(configMap) {
    steps.get("orca-config-step")
         .tasklet(configTasklet(configMap))
         .build()
  }

  private Tasklet configTasklet(configMap) {
    { StepContribution contribution, ChunkContext chunkContext ->
      configMap.each { Map<String, ?> entry ->
        entry.each {
          chunkContext.stepContext.stepExecution.jobExecution.executionContext.put("$entry.type.$it.key", it.value)
        }
      }
      return RepeatStatus.FINISHED
    } as Tasklet
  }

  // TODO: the type of the 2nd parameter here is annoying. I don't want to expose the build method on the Stage interface for SoC reasons
  private JobFlowBuilder stageFromConfig(JobFlowBuilder jobBuilder, StageBuilder stageBuilder) {
    stageBuilder.build(jobBuilder)
  }
}
