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
import com.netflix.spinnaker.orca.Status
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.monitoring.PipelineMonitor
import com.netflix.spinnaker.orca.pipeline.LinearStage
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.step.tasklet.TaskletStep
import static com.netflix.spinnaker.orca.batch.TaskTaskletAdapter.decorate
import static java.util.UUID.randomUUID

/**
 * A stub +Stage+ implementation for unit tests that doesn't need to be Spring-wired in order to work. It will
 * just add one or more pre-defined +Tasks+ (probably mocks) to the pipeline.
 */
@CompileStatic
class TestStage extends LinearStage {

  private final List<Task> tasks = []
  private final PipelineMonitor pipelineMonitor

  TestStage(String name, StepBuilderFactory steps, PipelineMonitor pipelineMonitor, Task... tasks) {
    super(name)
    this.steps = steps
    this.pipelineMonitor = pipelineMonitor
    this.tasks.addAll tasks
  }

  void addTasklet(Task task) {
    tasks << task
  }

  void leftShift(Task task) {
    addTasklet task
  }

  @Override
  protected List<Step> buildSteps() {
    def index = 0
    tasks.collect {
      index++
      buildStep it, index == 1, index == tasks.size()
    }
  }

  private TaskletStep buildStep(Task task, boolean first, boolean last) {
    def listener = new StepExecutionListener() {
      @Override
      void beforeStep(StepExecution stepExecution) {
        if (first) {
          pipelineMonitor.beginStage(name)
        }
        pipelineMonitor.beginTask()
      }

      @Override
      ExitStatus afterStep(StepExecution stepExecution) {
        pipelineMonitor.endTask(Status.valueOf(stepExecution.exitStatus.exitDescription))
        if (last || stepExecution.isTerminateOnly()) {
          pipelineMonitor.endStage(name)
        }
        return stepExecution.exitStatus
      }
    }

    steps.get(randomUUID().toString())
         .listener(listener)
         .tasklet(decorate(task))
         .build()
  }
}
