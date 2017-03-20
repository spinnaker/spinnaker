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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Renders a module with the given context and parameters.
 *
 * Usage:
 *
 * ```
 * {{module "myModuleId" foo=bar baz=bang}}
 * ```
 */
public class ModuleHelper implements Helper<Object> {

  private Renderer renderer;

  private ObjectMapper objectMapper;

  public ModuleHelper(Renderer renderer, ObjectMapper objectMapper) {
    this.renderer = renderer;
    this.objectMapper = objectMapper;
  }

  @Override
  public String apply(Object context, Options options) throws IOException {
    if (!(context instanceof String)) {
      throw new TemplateRenderException(new Error()
        .withMessage(String.format("Invalid module ID provided: %s", context))
        .withCause("Expected string, got: " + (context == null ? "null" : context.getClass().getSimpleName()))
      );
    }

    PipelineTemplate template = options.get("pipelineTemplate");
    if (template == null) {
      throw new TemplateRenderException(new Error()
        .withMessage("Pipeline template missing from handlebars context")
        .withCause("Internal error")
        .withLocation(String.format("module:%s", context))
      );
    }

    TemplateModule module = template.getModules()
      .stream()
      .filter(m -> m.getId().equals(context))
      .findFirst()
      .orElseThrow((Supplier<RuntimeException>) () -> new TemplateRenderException(String.format("Module does not exist by ID: %s", context)));

    DefaultRenderContext moduleContext = new DefaultRenderContext(options.get("application"), template, options.get("trigger"));
    moduleContext.setLocation("module:" + module.getId());

    List<String> missing = new ArrayList<>();
    for (NamedHashMap var : module.getVariables()) {
      Object val = options.hash(var.getName());
      if (val == null) {
        if (var.containsKey("defaultValue")) {
          val = var.get("defaultValue");
        } else {
          missing.add(var.getName());
          continue;
        }
      }
      moduleContext.getVariables().put(var.getName(), val);
    }
    if (missing.size() > 0) {
      throw new TemplateRenderException(new Error()
        .withMessage("Missing required variables in module")
        .withCause("'" + StringUtils.join(missing, "', '") + "' must be defined")
        .withLocation(moduleContext.getLocation())
      );
    }

    Object rendered = RenderUtil.deepRender(renderer, module.getDefinition(), moduleContext);

    return new String(objectMapper.writeValueAsBytes(rendered));
  }
}
