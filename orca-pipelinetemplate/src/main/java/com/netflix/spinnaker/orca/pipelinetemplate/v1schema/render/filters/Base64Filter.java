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

import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;

import java.util.Base64;

public class Base64Filter implements Filter {

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    if (var == null) {
      return null;
    }
    if (args.length != 0) {
      throw new InterpretException("base64 does not accept any arguments");
    }
    return new String(Base64.getEncoder().encode(var.toString().getBytes()));
  }

  @Override
  public String getName() {
    return "base64";
  }
}
