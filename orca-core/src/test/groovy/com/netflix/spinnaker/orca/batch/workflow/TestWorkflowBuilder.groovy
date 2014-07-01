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

package com.netflix.spinnaker.orca.batch.workflow

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.workflow.WorkflowBuilderSupport
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.FlowJobBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep

@CompileStatic
class TestWorkflowBuilder extends WorkflowBuilderSupport<SimpleJobBuilder> {

  private final Tasklet tasklet

  TestWorkflowBuilder(Tasklet tasklet, StepBuilderFactory steps) {
    this.tasklet = tasklet
    this.steps = steps
  }

  @Override
  SimpleJobBuilder build(JobBuilder jobBuilder) {
    jobBuilder.start(buildStep())
  }

  @Override
  SimpleJobBuilder build(SimpleJobBuilder jobBuilder) {
    jobBuilder.next(buildStep())
  }

  private TaskletStep buildStep() {
    steps.get(UUID.randomUUID().toString()).tasklet(tasklet).build()
  }
}
