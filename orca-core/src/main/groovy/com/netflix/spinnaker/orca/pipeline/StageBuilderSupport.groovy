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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.spring.AutowiredComponentBuilder
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilderHelper
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.batch.TaskTaskletAdapter.decorate

/**
 * Base class for a component that constructs a _stage_ to be run as (part of) a _pipeline_.
 */
@CompileStatic
abstract class StageBuilderSupport<B extends JobBuilderHelper<B>> implements AutowiredComponentBuilder, StageBuilder<B> {

  protected StepBuilderFactory steps
  private final String name

  StageBuilderSupport(String name) {
    this.name = name
  }

  /**
   * @return the stage name used by Mayo when configuring pipelines.
   */
  @Override
  final String getName() {
    name
  }

  /**
   * Builds and autowires a task.
   *
   * @param taskType The +Task+ implementation class.
   * @return a +Tasklet+ that wraps the task implementation. This can be appended to the job as a tasklet step.
   * @see org.springframework.batch.core.step.builder.StepBuilder#tasklet(org.springframework.batch.core.step.tasklet.Tasklet)
   */
  protected Tasklet buildTask(Class<? extends Task> taskType) {
    def task = taskType.newInstance()
    autowire task
    decorate task
  }

  @Autowired
  void setSteps(StepBuilderFactory steps) {
    this.steps = steps
  }
}
