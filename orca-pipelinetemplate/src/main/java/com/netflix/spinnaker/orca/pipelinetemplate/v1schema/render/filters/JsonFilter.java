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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;

public class JsonFilter implements Filter {

  private ObjectMapper objectMapper;

  public JsonFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    if (args.length != 0) {
      throw new InterpretException("json filter does accept any arguments");
    }

    try {
      return objectMapper.writeValueAsString(var);
    } catch (JsonProcessingException e) {
      throw TemplateRenderException.fromError(
        new Error()
          .withMessage("failed converting object to json")
          .withDetail("object", (String) var),
        e
      );
    }
  }

  @Override
  public String getName() {
    return "json";
  }
}
