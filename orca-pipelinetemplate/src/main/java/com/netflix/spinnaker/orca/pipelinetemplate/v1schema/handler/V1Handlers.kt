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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.V1SchemaExecutionGenerator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.GraphMutator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateConfigurationSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateSchemaValidator
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.stream.Collectors

@Component
class V1SchemaHandlerGroup
@Autowired constructor(
  private val templateLoader: TemplateLoader,
  private val renderer: Renderer,
  private val objectMapper: ObjectMapper,
  private val registry: Registry
) : HandlerGroup {

  override fun getHandlers(): List<Handler> =
    listOf(
      V1TemplateLoaderHandler(templateLoader, renderer, objectMapper),
      V1ConfigurationValidationHandler(),
      V1TemplateValidationHandler(),
      V1GraphMutatorHandler(renderer, registry),
      V1PipelineGenerator()
    )
}

class V1ConfigurationValidationHandler : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()

    val ctx = context.getSchemaContext<V1PipelineTemplateContext>()
    V1TemplateConfigurationSchemaValidator().validate(
      ctx.configuration,
      errors,
      V1TemplateConfigurationSchemaValidator.SchemaValidatorContext(
        ctx.template.stages.stream().map { it.id }.collect(Collectors.toList())
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}

class V1TemplateValidationHandler : Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val errors = Errors()
    V1TemplateSchemaValidator<V1TemplateSchemaValidator.SchemaValidatorContext>().validate(
      context.getSchemaContext<V1PipelineTemplateContext>().template,
      errors,
      V1TemplateSchemaValidator.SchemaValidatorContext(
        !context.getSchemaContext<V1PipelineTemplateContext>().configuration.stages.isEmpty()
      )
    )
    if (errors.hasErrors(context.getRequest().plan)) {
      context.getErrors().addAll(errors)
      chain.clear()
    }
  }
}

class V1GraphMutatorHandler(
  private val renderer: Renderer,
  private val registry: Registry
) : Handler {

  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val trigger = context.getRequest().trigger as MutableMap<String, Any>
    val ctx = context.getSchemaContext<V1PipelineTemplateContext>()

    val mutator = GraphMutator(ctx.configuration, renderer, registry, trigger)
    mutator.mutate(ctx.template)
  }
}

class V1PipelineGenerator : Handler {

  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val ctx = context.getSchemaContext<V1PipelineTemplateContext>()
    val generator = V1SchemaExecutionGenerator()
    context.getProcessedOutput().putAll(generator.generate(ctx.template, ctx.configuration, context.getRequest()))
  }
}
