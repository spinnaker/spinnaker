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

package com.netflix.spinnaker.halyard.config.problem.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

public class ConfigProblemSetBuilder {
  private List<ConfigProblemBuilder> builders = new ArrayList<>();

  @Getter private final ApplicationContext context;

  @Setter(AccessLevel.PUBLIC)
  private Node node;

  public ConfigProblemSetBuilder(ApplicationContext context) {
    this.context = context;
  }

  public ConfigProblemBuilder addProblem(Severity severity, String message) {
    return addProblem(severity, message, null);
  }

  public ConfigProblemBuilder addProblem(Severity severity, String message, String field) {
    ConfigProblemBuilder problemBuilder = new ConfigProblemBuilder(severity, message);
    if (node != null) {
      problemBuilder.setNode(node);

      if (field != null && !field.isEmpty()) {
        problemBuilder.setOptions(node.fieldOptions(new ConfigProblemSetBuilder(context), field));
      }
    }

    builders.add(problemBuilder);
    return problemBuilder;
  }

  public ConfigProblemSetBuilder extend(HalException e) {
    e.getProblems()
        .getProblems()
        .forEach(
            p ->
                addProblem(p.getSeverity(), p.getMessage())
                    .setOptions(p.getOptions())
                    .setRemediation(p.getRemediation()));

    return this;
  }

  public ProblemSet build() {
    List<Problem> problems =
        builders.stream().map(ConfigProblemBuilder::build).collect(Collectors.toList());

    return new ProblemSet(problems);
  }

  public ConfigProblemSetBuilder reset() {
    builders.clear();
    return this;
  }
}
