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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import org.apache.commons.lang3.math.NumberUtils;

public class JsonRenderedValueConverter implements RenderedValueConverter {

  private ObjectMapper pipelineTemplateObjectMapper;

  public JsonRenderedValueConverter(ObjectMapper pipelineTemplateObjectMapper) {
    this.pipelineTemplateObjectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public Object convertRenderedValue(String rendered) {
    if (NumberUtils.isNumber(rendered)) {
      if (rendered.contains(".")) {
        return NumberUtils.createDouble(rendered);
      }
      try {
        return NumberUtils.createInteger(rendered);
      } catch (NumberFormatException ignored) {
        return NumberUtils.createLong(rendered);
      }
    } else if (rendered.equals("true") || rendered.equals("false")) {
      return Boolean.parseBoolean(rendered);
    } else if (rendered.startsWith("{{")
        || (!rendered.startsWith("{") && !rendered.startsWith("["))) {
      return rendered;
    }

    JsonNode node;
    try {
      node = pipelineTemplateObjectMapper.readTree(rendered);
    } catch (IOException e) {
      throw new TemplateRenderException("template produced invalid json", e);
    }

    try {
      if (node.isArray()) {
        return pipelineTemplateObjectMapper.readValue(rendered, Collection.class);
      }
      if (node.isObject()) {
        return pipelineTemplateObjectMapper.readValue(rendered, HashMap.class);
      }
      if (node.isBoolean()) {
        return Boolean.parseBoolean(node.asText());
      }
      if (node.isDouble()) {
        return node.doubleValue();
      }
      if (node.canConvertToInt()) {
        return node.intValue();
      }
      if (node.canConvertToLong()) {
        return node.longValue();
      }
      if (node.isTextual()) {
        return node.textValue();
      }
      if (node.isNull()) {
        return null;
      }
    } catch (IOException e) {
      throw new TemplateRenderException("template produced invalid json", e);
    }

    throw new TemplateRenderException("unknown rendered object type");
  }
}
