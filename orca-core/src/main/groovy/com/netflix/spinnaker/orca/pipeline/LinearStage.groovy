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
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder

/**
 * A base class for +Stage+ implementations that just need to wire a linear sequence of steps.
 */
@CompileStatic
abstract class LinearStage extends StageSupport<SimpleJobBuilder> {

  LinearStage(String name) {
    super(name)
  }

  protected abstract List<Step> buildSteps()

  @Override
  SimpleJobBuilder build(JobBuilder jobBuilder) {
    def steps = buildSteps()
    wireSteps(jobBuilder.start(steps.first()), steps.tail())
  }

  @Override
  SimpleJobBuilder build(SimpleJobBuilder jobBuilder) {
    wireSteps(jobBuilder, buildSteps())
  }

  private SimpleJobBuilder wireSteps(SimpleJobBuilder jobBuilder, List<Step> steps) {
    (SimpleJobBuilder) steps.inject(jobBuilder) { SimpleJobBuilder builder, Step step ->
      builder.next(step)
    }
  }
}
