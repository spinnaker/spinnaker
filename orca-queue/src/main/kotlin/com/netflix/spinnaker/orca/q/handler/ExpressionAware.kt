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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Implemented by handlers that support expression evaluation.
 */
interface ExpressionAware {

  val contextParameterProcessor: ContextParameterProcessor
  val log: Logger
    get() = LoggerFactory.getLogger(javaClass)

  fun Stage.withMergedContext(): Stage {
    val processed = processEntries(this)
    val execution = execution
    this.context = object : MutableMap<String, Any?> by processed {
      override fun get(key: String): Any? {
        if (execution.type == PIPELINE) {
          if (key == "trigger") {
            return execution.trigger
          }

          if (key == "execution") {
            return execution
          }
        }

        val result = processed[key]

        if (result is String && ContextParameterProcessor.containsExpression(result)) {
          val augmentedContext = processed.augmentContext(execution)
          return contextParameterProcessor.process(mapOf(key to result), augmentedContext, true)[key]
        }

        return result
      }
    }
    return this
  }

  fun Stage.includeExpressionEvaluationSummary() {
    when {
      PipelineExpressionEvaluator.SUMMARY in this.context ->
        try {
          val expressionEvaluationSummary = this.context[PipelineExpressionEvaluator.SUMMARY] as Map<*, *>
          val evaluationErrors: List<String> = expressionEvaluationSummary.values.flatMap { (it as List<*>).map { (it as Map<*, *>)["description"] as String } }
          this.context["exception"] = mergedExceptionErrors(this.context["exception"] as Map<*, *>?, evaluationErrors)
        } catch (e: Exception) {
          log.error("failed to include expression evaluation error in context", e)
        }
    }
  }

  private fun mergedExceptionErrors(exception: Map<*, *>?, errors: List<String>): Map<*, *> =
    if (exception == null) {
      mapOf("details" to ExceptionHandler.responseDetails(PipelineExpressionEvaluator.ERROR, errors))
    } else {
      val details = exception["details"] as MutableMap<*, *>? ?: mutableMapOf("details" to mutableMapOf("errors" to mutableListOf<String>()))
      val mergedErrors: List<*> = (details["errors"] as List<*>? ?: mutableListOf<String>()) + errors
      mapOf("details" to mapOf("errors" to mergedErrors))
    }

  private fun processEntries(stage: Stage) =
    contextParameterProcessor.process(
      stage.context,
      stage.context.augmentContext(stage.execution),
      true
    )

  private fun Map<String, Any?>.augmentContext(execution: Execution) =
    if (execution.type == PIPELINE) {
      this + execution.context + mapOf("trigger" to execution.trigger, "execution" to execution)
    } else {
      this
    }
}
