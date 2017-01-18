/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.config.model.v1.problem;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Setter;

public class ProblemSetBuilder {
  private List<ProblemBuilder> builders = new ArrayList<>();

  @Setter(AccessLevel.PUBLIC)
  private Severity severity = Severity.NONE;

  @Setter(AccessLevel.PUBLIC)
  private Node node;

  public ProblemBuilder addProblem(Problem.Severity severity, String message) {
    return addProblem(severity, message, null);
  }

  public ProblemBuilder addProblem(Problem.Severity severity, String message, String field) {
    ProblemBuilder problemBuilder = new ProblemBuilder(severity, message);
    if (node != null) {
      problemBuilder.setReference(node.getReference());

      if (field != null && !field.isEmpty()) {
        problemBuilder.setOptions(node.fieldOptions(new ProblemSetBuilder(), field));
      }
    }

    builders.add(problemBuilder);
    return problemBuilder;
  }

  public ProblemSet build() {
    List<Problem> problems = builders
        .stream()
        .map(ProblemBuilder::build)
        .collect(Collectors.toList());

    ProblemSet result = new ProblemSet(problems);
    result.throwifSeverityExceeds(severity);

    return result;
  }
}
