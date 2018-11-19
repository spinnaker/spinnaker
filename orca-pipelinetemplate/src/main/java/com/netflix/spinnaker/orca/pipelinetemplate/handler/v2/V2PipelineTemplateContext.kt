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

package com.netflix.spinnaker.orca.pipelinetemplate.handler.v2

import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateSchemaContext
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate

data class V2PipelineTemplateContext(
  val configuration: TemplateConfiguration,
  val template: V2PipelineTemplate
) : PipelineTemplateSchemaContext
