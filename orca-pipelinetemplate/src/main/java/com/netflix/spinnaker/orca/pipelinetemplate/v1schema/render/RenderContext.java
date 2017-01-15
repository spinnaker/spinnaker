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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;

import java.util.HashMap;

public class RenderContext extends HashMap<String, Object> {

  private PipelineTemplate pipelineTemplate;

  public RenderContext(String application, PipelineTemplate pipelineTemplate) {
    put("application", application);
    this.pipelineTemplate = pipelineTemplate;
  }

  public boolean hasModule(String id) {
    return pipelineTemplate.getModules()
      .stream()
      .filter(m -> m.getId().equals(id))
      .findFirst()
      .map(m -> true)
      .orElse(false);
  }

  public boolean hasStage(String id) {
    return pipelineTemplate.getStages()
      .stream()
      .filter(s -> s.getId().equals(id))
      .findFirst()
      .map(s -> true)
      .orElse(false);
  }
}
