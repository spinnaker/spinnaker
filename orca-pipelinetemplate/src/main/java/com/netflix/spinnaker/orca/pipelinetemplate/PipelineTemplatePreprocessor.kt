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
package com.netflix.spinnaker.orca.pipelinetemplate

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.handler.DefaultHandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.GlobalPipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateErrorHandler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.SchemaVersionHandler
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.Nonnull
import javax.annotation.PostConstruct

@Component("pipelineTemplatePreprocessor")
class PipelineTemplatePreprocessor
@Autowired constructor(
  private val pipelineTemplateObjectMapper: ObjectMapper,
  private val schemaVersionHandler: SchemaVersionHandler,
  private val errorHandler: PipelineTemplateErrorHandler,
  private val registry: Registry
) : ExecutionPreprocessor {

  private val log = LoggerFactory.getLogger(javaClass)
  private val requestsId = registry.createId("mpt.requests")

  @PostConstruct fun confirmUsage() = log.info("Using ${javaClass.simpleName}")

  override fun supports(@Nonnull execution: MutableMap<String, Any>,
                        @Nonnull type: ExecutionPreprocessor.Type): Boolean = true

  override fun process(pipeline: MutableMap<String, Any>): MutableMap<String, Any> {
    // TODO(jacobkiefer): We push the 'toplevel' v2 config into a 'config' field to play nice
    // with MPT v1's opinionated TemplatedPipelineRequest. When we cut over, the template configuration
    // should be lifted to the top level like users will specify them.
    //
    // We also need to ensure that 'type' and 'schema' are set properly upstream when saving v2 template configs.
    if (pipeline.getOrDefault(V2PipelineTemplate.SCHEMA, null) == V2PipelineTemplate.V2_SCHEMA_VERSION &&
      pipeline.get("template") != null) {
      val templateConfig = HashMap(pipeline)
      templateConfig.remove("trigger") // template configurations don't have a 'trigger' field.
      pipeline.put("config", templateConfig)
      pipeline.put("type", "templatedPipeline")
    }


    val request = pipelineTemplateObjectMapper.convertValue(pipeline, TemplatedPipelineRequest::class.java)
    if (!request.isTemplatedPipelineRequest) {
      return pipeline
    }

    log.debug("Starting handler chain")

    val chain = DefaultHandlerChain()
    val context = GlobalPipelineTemplateContext(chain, request)

    chain.add(schemaVersionHandler)

    while (!chain.isEmpty()) {
      val handler = chain.removeFirst()
      try {
        handler.handle(chain, context)
      } catch (t: Throwable) {
        if (handler is PipelineTemplateErrorHandler) {
          recordRequest(context, false)
          throw IrrecoverableConditionException(t)
        }

        log.error("Unexpected error occurred while processing template: ", context.getRequest().getId(), t)
        context.getCaughtThrowables().add(t)
        chain.clear()
      }

      // Ensure the error handler is always the last thing we run
      if (chain.isEmpty() && handler !is PipelineTemplateErrorHandler) {
        chain.add(errorHandler)
      }
    }

    recordRequest(context, context.getErrors().hasErrors(false))

    log.debug("Handler chain complete")
    return context.getProcessedOutput()
  }

  private fun recordRequest(context: PipelineTemplateContext, success: Boolean) {
    registry.counter(requestsId.withTags(listOf(
      BasicTag("status", if (success) "success" else "failure"),
      BasicTag("schema", context.getRequest().schema ?: "unknown"),
      BasicTag("plan", context.getRequest().plan.toString())
    ))).increment()
  }
}

private class IrrecoverableConditionException(t: Throwable) : RuntimeException("Could not recover from an error condition", t)
