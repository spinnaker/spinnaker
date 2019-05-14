/*
 * Copyright 2018 Netflix, Inc.
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

import static java.lang.String.format;

import com.hubspot.jinjava.interpret.InterpretException;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MaxFilter implements Filter {

  @SuppressWarnings("unchecked")
  @Override
  public Object filter(Object var, JinjavaInterpreter interpreter, String... args) {
    if (args.length != 0) {
      throw new InterpretException("max filter does not accept any arguments");
    }

    if (!(var instanceof Collection)) {
      throw new InterpretException("max filter can only be used with a list type object");
    }

    List<Double> casted =
        (List<Double>)
            ((Collection) var)
                .stream()
                    .map(
                        v -> {
                          try {
                            return Double.valueOf(String.valueOf(v));
                          } catch (NumberFormatException e) {
                            throw new InterpretException(
                                format("max filter requires list of numeric values, %s given", v),
                                e);
                          }
                        })
                    .collect(Collectors.toList());

    if (casted.isEmpty()) {
      throw new InterpretException(
          "max filter must be provided a list of numeric values with at least one value");
    }

    Double max = Collections.max(casted);

    if (max % max.intValue() == 0) {
      return max.intValue();
    }
    return max;
  }

  @Override
  public String getName() {
    return "max";
  }
}
