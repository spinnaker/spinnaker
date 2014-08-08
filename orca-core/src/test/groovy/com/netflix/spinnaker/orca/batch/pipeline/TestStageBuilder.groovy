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
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep
import static java.util.UUID.randomUUID

/**
 * A stub +StageBuilder+ implementation for unit tests that doesn't need to be Spring-wired in order to work. It will
 * just add a single pre-defined +Tasklet+ (probably a mock) to the pipeline.
 */
@CompileStatic
class TestStageBuilder extends LinearStageBuilder {

  private final Tasklet tasklet

  TestStageBuilder(String name, Tasklet tasklet, StepBuilderFactory steps) {
    super(name)
    this.tasklet = tasklet
    this.steps = steps
  }

  @Override
  protected List<Step> buildSteps() {
    [buildStep()]
  }

  private TaskletStep buildStep() {
    steps.get(randomUUID().toString())
         .tasklet(tasklet)
         .build()
  }
}
