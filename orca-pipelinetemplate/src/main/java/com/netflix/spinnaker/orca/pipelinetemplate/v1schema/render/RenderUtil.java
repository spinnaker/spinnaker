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
      // TODO rz - add better debugging capabilities
      throw new TemplateRenderException(String.format("cannot exceed %d levels of depth in handlebars rendering", MAX_RENDER_DEPTH));
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
    throw new TemplateRenderException("unknown template type, cannot render: " + obj.getClass().getSimpleName());
  }

  private static boolean isPrimitive(Object o) {
    return CharSequence.class.isInstance(o) || Number.class.isInstance(o) || Boolean.class.isInstance(o);
  }
}
