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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.filters;

import com.hubspot.jinjava.interpret.Context;
import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.DefaultRenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Base64Filter implements Filter {

  private Renderer renderer;

  public Base64Filter(Renderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    if (var == null) {
      return null;
    }
    if (args.length != 0) {
      throw new InterpretException("base64 does not accept any arguments");
    }

    Context context = interpreter.getContext();

    RenderContext renderContext =
        new DefaultRenderContext(
            (String) context.get("application"),
            (PipelineTemplate) context.get("pipelineTemplate"),
            (Map<String, Object>) context.get("trigger", new HashMap<>()));
    renderContext.setLocation("base64");
    renderContext.getVariables().putAll(context);

    Object value = var;
    try {
      value = RenderUtil.deepRender(renderer, value, renderContext);
    } catch (InterpretException e) {
      throw TemplateRenderException.fromError(
          new Errors.Error()
              .withMessage("Failed rendering base64 contents")
              .withLocation(renderContext.getLocation())
              .withDetail("source", value.toString()),
          e);
    }

    return Base64.getEncoder().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String getName() {
    return "base64";
  }
}
