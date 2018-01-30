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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class RenderUtil {

  private final static int MAX_RENDER_DEPTH = 5;

  public static Object deepRender(Renderer renderer, Object obj, RenderContext context) {
    return deepRender(renderer, obj, context, 0);
  }

  @SuppressWarnings("unchecked")
  private static Object deepRender(Renderer renderer, Object obj, RenderContext context, int depth) {
    if (depth >= MAX_RENDER_DEPTH) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage(String.format("Cannot exceed %d levels of depth in handlebars rendering", MAX_RENDER_DEPTH))
          .withSuggestion("Try breaking up your templates into smaller, more reusable chunks via modules")
          .withLocation(context.getLocation())
      );
    }

    if (obj == null) {
      return null;
    }
    if (isPrimitive(obj)) {
      if (CharSequence.class.isInstance(obj)) {
        // If the rendered result is another object graph, we need to go deeper and ensure
        // all handlebars templates in that object graph get rendered. FOR INFINITY (or MAX_RENDER_DEPTH).
        Object rendered = renderer.renderGraph((String) obj, context);
        if (rendered instanceof Collection || rendered instanceof Map) {
          return deepRender(renderer, rendered, context, depth + 1);
        }
        return rendered;
      }
      return obj;
    }
    if (obj instanceof Collection) {
      List<Object> objList = new ArrayList<>();
      for (Object o : ((Collection) obj)) {
        objList.add(deepRender(renderer, o, context, depth));
      }
      return objList;
    }
    if (obj instanceof Map) {
      Map<String, Object> objMap = new LinkedHashMap<>();
      for (Entry<Object, Object> e : ((Map<Object, Object>) obj).entrySet()) {
        objMap.put((String) e.getKey(), deepRender(renderer, e.getValue(), context, depth));
      }
      return objMap;
    }

    throw TemplateRenderException.fromError(
      new Error()
        .withMessage("Unknown rendered type, cannot continue")
        .withCause("Unhandled type: " + obj.getClass().getSimpleName())
        .withSuggestion("Expected types: primitives, collections and maps")
        .withLocation(context.getLocation())
    );
  }

  private static boolean isPrimitive(Object o) {
    return CharSequence.class.isInstance(o) || Number.class.isInstance(o) || Boolean.class.isInstance(o);
  }

  public static RenderContext createDefaultRenderContext(PipelineTemplate template, TemplateConfiguration configuration, Map<String, Object> trigger) {
    RenderContext context = new DefaultRenderContext(configuration.getPipeline().getApplication(), template, trigger);
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
