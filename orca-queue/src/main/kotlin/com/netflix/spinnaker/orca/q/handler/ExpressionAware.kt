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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluationSummary
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.StageContext
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Implemented by handlers that support expression evaluation.
 */
interface ExpressionAware {

  val contextParameterProcessor: ContextParameterProcessor

  companion object {
    val mapper: ObjectMapper = OrcaObjectMapper.newInstance()
  }

  val log: Logger
    get() = LoggerFactory.getLogger(javaClass)

  fun Stage.withMergedContext(): Stage {
    val evalSummary = ExpressionEvaluationSummary()
    val processed = processEntries(this, evalSummary)
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

    // Clean up errors: since expressions are evaluated multiple times, it's possible that when
    // they were evaluated before the execution started not all data was available and the evaluation failed for
    // some property. If that evaluation subsequently succeeds, make sure to remove past error messages from the
    // context. Otherwise, it's very confusing in the UI because the value is clearly correctly evaluated but
    // the error is still shown
    if (hasFailedExpressions()) {
      try {
        val failedExpressions = this.context[PipelineExpressionEvaluator.SUMMARY] as MutableMap<String, *>

        val keysToRemove: List<String> = failedExpressions.keys.filter { expressionKey ->
          (evalSummary.wasAttempted(expressionKey) && !evalSummary.hasFailed(expressionKey))
        }.toList()

        keysToRemove.forEach { expressionKey ->
          failedExpressions.remove(expressionKey)
        }
      } catch (e: Exception) {
        // Best effort clean up, if if fails just log the error and leave the context be
        log.error("Failed to remove stale expression errors", e)
      }
    }

    return this
  }

  fun Stage.includeExpressionEvaluationSummary() {
    when {
      hasFailedExpressions() ->
        try {
          val expressionEvaluationSummary = this.context[PipelineExpressionEvaluator.SUMMARY] as Map<*, *>
          val evaluationErrors: List<String> = expressionEvaluationSummary.values.flatMap { (it as List<*>).map { (it as Map<*, *>)["description"] as String } }
          this.context["exception"] = mergedExceptionErrors(this.context["exception"] as Map<*, *>?, evaluationErrors)
        } catch (e: Exception) {
          log.error("failed to include expression evaluation error in context", e)
        }
    }
  }

  fun Stage.hasFailedExpressions(): Boolean =
    (PipelineExpressionEvaluator.SUMMARY in this.context) &&
    ((this.context[PipelineExpressionEvaluator.SUMMARY] as Map<*, *>).size > 0)

  fun Stage.shouldFailOnFailedExpressionEvaluation(): Boolean {
    return this.hasFailedExpressions() && this.context.containsKey("failOnFailedExpressions")
      && this.context["failOnFailedExpressions"] as Boolean
  }

  private fun mergedExceptionErrors(exception: Map<*, *>?, errors: List<String>): Map<*, *> =
    if (exception == null) {
      mapOf("details" to ExceptionHandler.responseDetails(PipelineExpressionEvaluator.ERROR, errors))
    } else {
      val details = exception["details"] as MutableMap<*, *>? ?: mutableMapOf("details" to mutableMapOf("errors" to mutableListOf<String>()))
      val mergedErrors: List<*> = (details["errors"] as List<*>? ?: mutableListOf<String>()) + errors
      mapOf("details" to mapOf("errors" to mergedErrors))
    }

  private fun processEntries(stage: Stage, summary: ExpressionEvaluationSummary): StageContext =
    StageContext(stage, contextParameterProcessor.process(
      stage.context,
      (stage.context as StageContext).augmentContext(stage.execution),
      true,
      summary
    )
    )

  // TODO (mvulfson): Ideally, we opt out of this method and use ContextParameterProcessor.buildExecutionContext
  // but that doesn't generate StageContext preventing us from doing recursive lookups... An investigation for another day
  private fun StageContext.augmentContext(execution: Execution): StageContext =
    if (execution.type == PIPELINE) {
      this + mapOf(
        "trigger" to mapper.convertValue<Map<String, Any>>(execution.trigger),
        "execution" to execution)
    } else {
      this
    }

  private operator fun StageContext.plus(map: Map<String, Any?>): StageContext
    = StageContext(this).apply { putAll(map) }

}
