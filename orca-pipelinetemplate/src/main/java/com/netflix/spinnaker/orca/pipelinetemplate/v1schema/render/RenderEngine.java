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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Component
public class RenderEngine {

  // TODO Renderer should support being given a raw hashmap to use as context.
  Renderer renderer;

  @Autowired
  public RenderEngine(Renderer renderer) {
    this.renderer = renderer;
  }


  public void render(PipelineTemplate template, TemplateConfiguration templateConfiguration) {
    RenderContext context = new RenderContext(templateConfiguration.getPipeline().getApplication(), template);
    context.putAll(templateConfiguration.getPipeline().getVariables());

    // Modules don't have implicit access to the variables of the template. Variables must be
    // explicitly defined and passed. (This is handled in the renderer impl)
    RenderContext moduleContext = new RenderContext(templateConfiguration.getPipeline().getApplication(), template);

    renderModules(template.getModules(), moduleContext);
    renderStages(template.getStages(), context);

    renderModules(templateConfiguration.getModules(), moduleContext);
    renderStages(templateConfiguration.getStages(), context);
  }

  private void renderModules(List<TemplateModule> modules, RenderContext context) {
    for (TemplateModule module : modules) {
      module.setWhen(renderWhen(module.getWhen(), context));
      module.setDefinition(deepRender(module.getDefinition(), context));
    }
  }

  @SuppressWarnings("unchecked")
  private void renderStages(List<StageDefinition> stages, RenderContext context) {
    for (StageDefinition stage : stages) {
      stage.setWhen(renderWhen(stage.getWhen(), context));

      Object rendered = deepRender(stage.getConfig(), context);
      if (!rendered.getClass().isAssignableFrom(Map.class)) {
        throw new IllegalTemplateConfigurationException("A stage's rendered config must be a map");
      }
      stage.setConfig((Map<String, Object>) rendered);
    }
  }

  @SuppressWarnings("unchecked")
  private Object deepRender(Object obj, RenderContext context) {
    if (obj.getClass().isPrimitive()) {
      if (obj.getClass().isAssignableFrom(CharSequence.class)) {
        return renderer.render((String) obj, context);
      }
      return obj;
    }
    if (obj.getClass().isArray()) {
      List<Object> objList = new ArrayList<>();
      for (Object o : ((Collection) obj)) {
        objList.add(deepRender(o, context));
      }
      return objList;
    }
    if (obj.getClass().isInstance(Map.class)) {
      Map<String, Object> objMap = new HashMap<>();
      for (Entry<Object, Object> e : ((Map<Object, Object>) obj).entrySet()) {
        objMap.put((String) e.getKey(), deepRender(e.getValue(), context));
      }
      return objMap;
    }
    throw new TemplateRenderException("unknown template type, cannot render: " + obj.getClass().getSimpleName());
  }

  private Boolean renderWhen(Object when, RenderContext context) {
    if (when == null) {
      return true;
    }
    return (Boolean) renderer.render((String) when, context);
  }
}
