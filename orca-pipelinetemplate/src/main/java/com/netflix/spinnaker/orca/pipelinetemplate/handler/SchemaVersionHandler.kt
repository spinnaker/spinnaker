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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.V1SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2.V2SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SchemaVersionHandler
@Autowired constructor(
  private val v1SchemaHandlerGroup: V1SchemaHandlerGroup,
  private val v2SchemaHandlerGroup: V2SchemaHandlerGroup
): Handler {
  override fun handle(chain: HandlerChain, context: PipelineTemplateContext) {
    when(context.getRequest().schema) {
      "1" -> chain.add(v1SchemaHandlerGroup)
      "v2" -> chain.add(v2SchemaHandlerGroup)
      else -> context.getErrors().add(
        Errors.Error().withMessage("unexpected schema version '${context.getRequest().schema}'")
      )
    }
  }
}
