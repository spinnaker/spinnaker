/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.handler

import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors

interface PipelineTemplateContext {
  fun getChain(): HandlerChain
  fun getErrors(): Errors
  fun getProcessedOutput(): MutableMap<String, Any>
  fun getRequest(): TemplatedPipelineRequest
  fun getCaughtThrowables(): MutableList<Throwable>
  fun <T : PipelineTemplateSchemaContext> setSchemaContext(c: T)
  fun <T : PipelineTemplateSchemaContext> getSchemaContext(): T
}

interface PipelineTemplateSchemaContext

class GlobalPipelineTemplateContext(
  private val chain: HandlerChain,
  private val request: TemplatedPipelineRequest
) : PipelineTemplateContext {

  private val errors = Errors()
  private val processedOutput = mutableMapOf<String, Any>()
  private val throwables = mutableListOf<Throwable>()

  private var schemaContext: PipelineTemplateSchemaContext? = null
  override fun getChain() = chain
  override fun getErrors() = errors
  override fun getProcessedOutput() = processedOutput
  override fun getRequest() = request
  override fun getCaughtThrowables() = throwables

  override fun <T : PipelineTemplateSchemaContext> setSchemaContext(c: T) {
    if (schemaContext != null) {
      throw IllegalArgumentException("schema context already set")
    }
    schemaContext = c
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : PipelineTemplateSchemaContext> getSchemaContext(): T {
    if (schemaContext == null) {
      throw IllegalStateException("schema context has not been set yet")
    }
    return schemaContext as T
  }
}
