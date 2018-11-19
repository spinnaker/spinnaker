/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.handler.v2.V2PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.v2.V2TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2.V2TemplateConfigurationSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2.V2TemplateSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.stream.Collectors

@Component
class V2SchemaHandlerGroup
@Autowired constructor(
  private val templateLoader: V2TemplateLoader,
  private val objectMapper: ObjectMapper,
  private val contextParameterProcessor: ContextParameterProcessor,
  private val registry: Registry
): HandlerGroup {

  override fun getHandlers(): List<Handler>
    = listOf(
    V2TemplateLoaderHandler(templateLoader, contextParameterProcessor, objectMapper),
    V2ConfigurationValidationHandler(),
    V2TemplateValidationHandler()
  )
}

class V2ConfigurationValidationHandler : Handler {
  private val log = LoggerFactory.getLogger(V2ConfigurationValidationHandler::class.java)
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()

    val ctx = context.getSchemaContext<V2PipelineTemplateContext>()
    V2TemplateConfigurationSchemaValidator().validate(
      ctx.configuration,
      errors,
      V2TemplateConfigurationSchemaValidator.SchemaValidatorContext(
        ctx.template.stages.stream().map { it.id }.collect(Collectors.toList())
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}

class V2TemplateValidationHandler : Handler {
  private val log = LoggerFactory.getLogger(V2TemplateValidationHandler::class.java)
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()
    V2TemplateSchemaValidator<V2TemplateSchemaValidator.SchemaValidatorContext>().validate(
      context.getSchemaContext<V2PipelineTemplateContext>().template,
      errors,
      V2TemplateSchemaValidator.SchemaValidatorContext(
        !context.getSchemaContext<V2PipelineTemplateContext>().configuration.stages.isEmpty()
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}
