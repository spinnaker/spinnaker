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

import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import groovy.transform.CompileStatic
import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import static groovy.lang.Closure.DELEGATE_FIRST
import static java.lang.System.currentTimeMillis

@CompileStatic
class ExecutionBuilder {

  /**
   * Constructs and returns a {@link PipelineExecutionImpl} instance.
   *
   * @param builder used for customizing the pipeline.
   * @return a pipeline.
   */
  static PipelineExecution pipeline(
    @DelegatesTo(value = PipelineExecution, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def pipeline = PipelineExecutionImpl.newPipeline("covfefe")
    pipeline.trigger = new DefaultTrigger("manual", null, "user@example.com")
    pipeline.buildTime = currentTimeMillis()

    builder.delegate = pipeline
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return pipeline
  }

  /**
   * Constructs and returns a {@link PipelineExecutionImpl} instance.
   *
   * @param builder used for customizing the orchestration.
   * @return an orchestration.
   */
  static PipelineExecution orchestration(
    @DelegatesTo(value = PipelineExecution, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def orchestration = PipelineExecutionImpl.newOrchestration("covfefe")
    orchestration.buildTime = currentTimeMillis()
    orchestration.trigger = new DefaultTrigger("manual")

    builder.delegate = orchestration
    builder.resolveStrategy = DELEGATE_FIRST
    builder()

    return orchestration
  }

  /**
   * Constructs and returns a {@link StageExecutionImpl} instance.
   *
   * @param builder used for customizing the stage.
   * @return a stage.
   */
  static StageExecution stage(
    @DelegatesTo(value = StageExecution, strategy = DELEGATE_FIRST)
      Closure builder = {}) {
    def stage = new StageExecutionImpl()
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

  private static PipelineExecution findExecution(Closure closure) {
    if (closure.owner instanceof Closure) {
      def enclosingClosure = (closure.owner as Closure)
      if (enclosingClosure.delegate instanceof PipelineExecutionImpl) {
        return enclosingClosure.delegate as PipelineExecutionImpl
      } else {
        return findExecution(enclosingClosure)
      }
    } else {
      return null
    }
  }

  private static StageExecution findParentStage(Closure closure) {
    if (closure.owner instanceof Closure) {
      def enclosingClosure = (closure.owner as Closure)
      if (enclosingClosure.delegate instanceof StageExecution) {
        return enclosingClosure.delegate as StageExecution
      } else {
        return findParentStage(enclosingClosure)
      }
    } else {
      return null
    }
  }

  private ExecutionBuilder() {}
}
