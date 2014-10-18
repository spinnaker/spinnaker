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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.CompletedBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.batch.lifecycle.BatchExecutionSpec
import com.netflix.spinnaker.orca.pipeline.Stage
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.flow.FlowJob
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import static org.hamcrest.Matchers.containsInAnyOrder
import static spock.util.matcher.HamcrestSupport.expect

class BakeStageSpec extends BatchExecutionSpec {

  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps
  def bakery = Mock(BakeryService)

  @Override
  protected Job configureJob(JobBuilder jobBuilder) {
    def stage = new BakeStage()
    applicationContext.beanFactory.with {
      autowireBean stage
      registerSingleton "bakery", bakery
      registerSingleton "objectMapper", new ObjectMapper()
    }
    stage.build(jobBuilder, new Stage("bake")).build().build()
  }

  def "builds a bake stage and its tasks"() {
    when:
    launchJob()

    then:
    jobLauncherTestUtils.job instanceof FlowJob
    def steps = ((FlowJob) jobLauncherTestUtils.job).stepNames.collect {
      jobLauncherTestUtils.job.getStep(it)
    }
    steps.size() == 3
    steps.every { it instanceof TaskletStep }
    steps.every { ((TaskletStep) it).tasklet instanceof TaskTaskletAdapter }
    expect steps.tasklet.taskType, containsInAnyOrder(CreateBakeTask, MonitorBakeTask, CompletedBakeTask)
  }
}
