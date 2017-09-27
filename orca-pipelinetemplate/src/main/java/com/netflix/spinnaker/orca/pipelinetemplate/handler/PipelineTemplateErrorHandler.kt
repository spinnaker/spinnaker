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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import org.springframework.stereotype.Component

@Component
class PipelineTemplateErrorHandler : Handler {

  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    context.getCaughtThrowables().map { generateErrors(it) }.forEach { context.getErrors().addAll(it) }

    if (context.getErrors().hasErrors(context.getRequest().plan)) {
      context.getProcessedOutput().putAll(context.getErrors().toResponse())
    }
  }

  // Gross backwards compat with old error handler logic
  private fun generateErrors(t: Throwable): Errors {
    val e = Errors()
    if (t is TemplateLoaderException) {
      e.add(Errors.Error().withMessage("failed loading template").withCause(t.message))
    } else if (t is TemplateRenderException) {
      if (!e.hasErrors(true)) {
        e.add(Errors.Error().withMessage("failed rendering template expression").withCause(t.message))
      }
    } else if (t is IllegalTemplateConfigurationException) {
      e.add(
        if (t.error != null) t.error else Errors.Error().withMessage("malformed template configuration").withCause(t.message)
      )
    } else {
      e.add(Errors.Error().withMessage("unexpected error").withCause(t.toString()))
    }
    return e
  }
}
