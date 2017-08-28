/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.test.model

import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import static groovy.lang.Closure.DELEGATE_FIRST
import static java.lang.System.currentTimeMillis

@CompileStatic
class ExecutionBuilder {

  /**
   * Constructs and returns a {@link Pipeline} instance.
   *
   * @param builder used for customizing the pipeline.
   * @return a pipeline.
   */
  static Pipeline pipeline(
    @DelegatesTo(value = Pipeline, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def pipeline = new Pipeline("covfefe")
    pipeline.buildTime = currentTimeMillis()

    builder.delegate = pipeline
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return pipeline
  }

  /**
   * Constructs and returns a {@link Orchestration} instance.
   *
   * @param builder used for customizing the orchestration.
   * @return an orchestration.
   */
  static Orchestration orchestration(
    @DelegatesTo(value = Orchestration, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def orchestration = new Orchestration("covfefe")
    orchestration.buildTime = currentTimeMillis()

    builder.delegate = orchestration
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return orchestration
  }

  /**
   * Constructs and returns a {@link Stage} instance.
   *
   * @param builder used for customizing the stage.
   * @return a stage.
   */
  static Stage<Pipeline> stage(
    @DelegatesTo(value = Stage, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def stage = new Stage<>()
    stage.type = "test"

    def execution = findExecution(builder) ?: pipeline()
    stage.execution = execution
    execution.stages << stage

    builder.delegate = stage
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return stage
  }

  private static Pipeline findExecution(Closure closure) {
    if (closure.owner instanceof Closure) {
      def enclosingClosure = (closure.owner as Closure)
      if (enclosingClosure.delegate instanceof Pipeline) {
        return enclosingClosure.delegate as Pipeline
      } else {
        return findExecution(enclosingClosure)
      }
    } else {
      return null
    }
  }

  private ExecutionBuilder() {}
}
