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

package com.netflix.spinnaker.orca.bakery.job

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.TaskTaskletAdapter.decorate

@Component
@CompileStatic
class BakeJobBuilder {

  private ApplicationContext applicationContext
  private StepBuilderFactory steps

  SimpleJobBuilder build(JobBuilder jobBuilder) {
    def step1 = steps.get("CreateBakeStep")
      .tasklet(buildTask(CreateBakeTask))
      .build()
    def step2 = steps.get("MonitorBakeStep")
      .tasklet(buildTask(MonitorBakeTask))
      .build()
    jobBuilder
      .start(step1)
      .next(step2)
  }

  private Tasklet buildTask(Class<? extends Task> taskType) {
    def task = taskType.newInstance()
    autowire task
    decorate task
  }

  // TODO: great candidate for a trait
  void autowire(obj) {
    applicationContext.autowireCapableBeanFactory.autowireBean(obj)
  }

  @Autowired
  void setSteps(StepBuilderFactory steps) {
    this.steps = steps
  }

  @Autowired
  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }
}
