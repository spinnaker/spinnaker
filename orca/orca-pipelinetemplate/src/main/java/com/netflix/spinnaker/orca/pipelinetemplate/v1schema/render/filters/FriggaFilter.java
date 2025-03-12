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
import com.netflix.frigga.Names;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FriggaFilter implements Filter {

  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    String input = (String) var;
    if (args.length != 1) {
      throw new InterpretException(
          "frigga filter requires 1 arg (the name of the frigga part to return)");
    }

    String methodName = "get" + args[0].substring(0, 1).toUpperCase() + args[0].substring(1);

    Method accessor;
    try {
      accessor = Names.class.getMethod(methodName);
    } catch (NoSuchMethodException e) {
      throw new InterpretException("frigga filter cannot find Frigga method: " + methodName, e);
    }

    Names names = Names.parseName(input);
    try {
      return accessor.invoke(names);
    } catch (IllegalAccessException e) {
      throw new InterpretException("frigga filter provided invalid name (illegal access)");
    } catch (InvocationTargetException e) {
      throw new InterpretException("frigga filter provided failed to execute successfully", e);
    }
  }

  @Override
  public String getName() {
    return "frigga";
  }
}
