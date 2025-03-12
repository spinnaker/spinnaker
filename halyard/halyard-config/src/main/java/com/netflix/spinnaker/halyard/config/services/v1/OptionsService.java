/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OptionsService {
  @Autowired LookupService lookupService;

  @Autowired ApplicationContext applicationContext;

  public <T extends Node> FieldOptions options(
      NodeFilter filter, Class<T> nodeClass, String field) {
    ConfigProblemSetBuilder problemSetBuilder = new ConfigProblemSetBuilder(applicationContext);
    List<T> nodes = lookupService.getMatchingNodesOfType(filter, nodeClass);
    List<String> options =
        nodes.stream()
            .map(
                n -> {
                  problemSetBuilder.setNode(n);
                  return n.fieldOptions(problemSetBuilder, field);
                })
            .reduce(
                new ArrayList<>(),
                (a, b) -> {
                  a.addAll(b);
                  return a;
                });

    return new FieldOptions().setOptions(options).setProblemSet(problemSetBuilder.build());
  }

  @Data
  public static class FieldOptions implements DaemonResponse.FieldOptions {
    ProblemSet problemSet;
    List<String> options;
  }
}
