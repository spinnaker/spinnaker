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
import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors

class V1TemplateLoaderHandler(
  private val templateLoader: TemplateLoader,
  private val renderer: Renderer,
  private val objectMapper: ObjectMapper
) : Handler {

  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    val config = objectMapper.convertValue(context.getRequest().config, TemplateConfiguration::class.java)

    // Allow template inlining to perform plans without publishing the template
    if (context.getRequest().plan && context.getRequest().template != null) {
      val template = objectMapper.convertValue(context.getRequest().template, PipelineTemplate::class.java)
      context.setSchemaContext(V1PipelineTemplateContext(config, template))
      return
    }

    if (config.pipeline.template == null) {
      context.getErrors().add(Errors.Error().withMessage("configuration is missing a template"))
      return
    }

    val trigger = context.getRequest().trigger as MutableMap<String, Any>?

    setTemplateSourceWithJinja(config, trigger)
    val templates = templateLoader.load(config.pipeline.template)

    val mergedTemplate = TemplateMerge.merge(templates)

    // ensure that any expressions contained with template variables are rendered
    val renderContext = DefaultRenderContext(config.pipeline.application, mergedTemplate, trigger)
    renderTemplateVariables(renderContext, mergedTemplate)

    context.setSchemaContext(V1PipelineTemplateContext(
      config,
      mergedTemplate
    ))
  }


  private fun renderTemplateVariables(renderContext: RenderContext, pipelineTemplate: PipelineTemplate) {
    if (pipelineTemplate.variables == null) {
      return
    }

    pipelineTemplate.variables.forEach { v ->
      val value = v.defaultValue
      if (value != null && value is String) {
        v.defaultValue = renderer.renderGraph(value.toString(), renderContext)
      }
    }
  }

  private fun setTemplateSourceWithJinja(tc: TemplateConfiguration, trigger: MutableMap<String, Any>?) {
    if (trigger == null) {
      return
    }
    val context = DefaultRenderContext(tc.pipeline.application, null, trigger)
    tc.pipeline.template.source = renderer.render(tc.pipeline.template.source, context)
  }
}
