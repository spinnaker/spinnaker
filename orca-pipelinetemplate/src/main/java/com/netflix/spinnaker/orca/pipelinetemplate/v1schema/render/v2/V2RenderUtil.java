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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.v2;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;

import java.util.Map;

public class V2RenderUtil {

  public static RenderContext createDefaultRenderContext(V2PipelineTemplate template, TemplateConfiguration configuration, Map<String, Object> trigger) {
    RenderContext context = new V2DefaultRenderContext(configuration.getPipeline().getApplication(), template, trigger);
    if (template != null && template.getVariables() != null) {
      template.getVariables().stream()
        .filter(v -> (v.isNullable() && v.getDefaultValue() == null) || v.hasDefaultValue())
        .forEach(v -> context.getVariables().put(v.getName(), v.getDefaultValue()));
    }
    if (configuration.getPipeline().getVariables() != null) {
      context.getVariables().putAll(configuration.getPipeline().getVariables());
    }
    return context;
  }
}
