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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class V2DefaultRenderContext implements RenderContext {
  private Map<String, Object> variables = new HashMap<>();
  private String location;

  private V2DefaultRenderContext(V2DefaultRenderContext source) {
    this.variables = new HashMap<>(source.getVariables());
    this.location = source.getLocation();
  }

  public V2DefaultRenderContext(String application, V2PipelineTemplate pipelineTemplate, Map<String, Object> trigger) {
    variables.put("application", application);
    variables.put("pipelineTemplate", pipelineTemplate);
    variables.put("trigger", trigger);
  }

  @Override
  public RenderContext copy() {
    return new V2DefaultRenderContext(this);
  }
}
