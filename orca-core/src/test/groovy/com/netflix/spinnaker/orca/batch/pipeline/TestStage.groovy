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

package com.netflix.spinnaker.orca.batch.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory

/**
 * A stub +Stage+ implementation for unit tests that doesn't need to be Spring-wired in order to work. It will
 * just add one or more pre-defined +Tasks+ (probably mocks) to the pipeline.
 */
@CompileStatic
class TestStage extends LinearStage {

  private final List<Task> tasks = []

  TestStage(String name, StepBuilderFactory steps, ExecutionRepository executionRepository, Task... tasks) {
    super(name)
    this.steps = steps
    this.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
//    this.taskListeners = [
//        new StageStatusPropagationListener(executionRepository)
//    ]
    this.tasks.addAll tasks
  }

  void addTasklet(Task task) {
    tasks << task
  }

  TestStage leftShift(Task task) {
    addTasklet task
    return this
  }

  @Override
  protected List<Step> buildSteps(Stage stage) {
    def i = 1
    tasks.collect {
      buildStep stage, "task${i++}", it
    }
  }
}
