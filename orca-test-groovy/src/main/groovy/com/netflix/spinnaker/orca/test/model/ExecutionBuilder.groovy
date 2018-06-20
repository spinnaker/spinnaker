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

import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static groovy.lang.Closure.DELEGATE_FIRST
import static java.lang.System.currentTimeMillis

@CompileStatic
class ExecutionBuilder {

  /**
   * Constructs and returns a {@link Execution} instance.
   *
   * @param builder used for customizing the pipeline.
   * @return a pipeline.
   */
  static Execution pipeline(
    @DelegatesTo(value = Execution, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def pipeline = Execution.newPipeline("covfefe")
    pipeline.trigger = new DefaultTrigger("manual", null, "user@example.com")
    pipeline.buildTime = currentTimeMillis()

    builder.delegate = pipeline
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return pipeline
  }

  /**
   * Constructs and returns a {@link Execution} instance.
   *
   * @param builder used for customizing the orchestration.
   * @return an orchestration.
   */
  static Execution orchestration(
    @DelegatesTo(value = Execution, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def orchestration = Execution.newOrchestration("covfefe")
    orchestration.buildTime = currentTimeMillis()
    orchestration.trigger = new DefaultTrigger("manual")

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
  static Stage stage(
    @DelegatesTo(value = Stage, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def stage = new Stage()
    stage.type = "test"

    def execution = findExecution(builder) ?: pipeline()
    stage.execution = execution
    execution.stages << stage

    def parentStage = findParentStage(builder)
    if (parentStage) {
      stage.parentStageId = parentStage.id
      if (stage.syntheticStageOwner == null) {
        stage.syntheticStageOwner = STAGE_BEFORE
      }
    }

    builder.delegate = stage
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return stage
  }

  private static Execution findExecution(Closure closure) {
    if (closure.owner instanceof Closure) {
      def enclosingClosure = (closure.owner as Closure)
      if (enclosingClosure.delegate instanceof Execution) {
        return enclosingClosure.delegate as Execution
      } else {
        return findExecution(enclosingClosure)
      }
    } else {
      return null
    }
  }

  private static Stage findParentStage(Closure closure) {
    if (closure.owner instanceof Closure) {
      def enclosingClosure = (closure.owner as Closure)
      if (enclosingClosure.delegate instanceof Stage) {
        return enclosingClosure.delegate as Stage
      } else {
        return findParentStage(enclosingClosure)
      }
    } else {
      return null
    }
  }

  private ExecutionBuilder() {}
}
