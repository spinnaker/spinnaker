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

package com.netflix.spinnaker.halyard.config.model.v1;

import com.netflix.spinnaker.halyard.config.model.v1.HalconfigProblem.Severity;
import lombok.AccessLevel;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HalconfigProblemSetBuilder {
  private List<HalconfigProblemBuilder> builders = new ArrayList<>();

  @Setter(AccessLevel.PUBLIC)
  private HalconfigCoordinates coordinates;

  public HalconfigProblemBuilder addProblem(Severity severity, String message) {
    HalconfigProblemBuilder problemBuilder = new HalconfigProblemBuilder(severity, message);
    problemBuilder.setCoordinates(coordinates);
    builders.add(problemBuilder);
    return problemBuilder;
  }

  public HalconfigProblemSet build() {
    List<HalconfigProblem> problems = builders
        .stream()
        .map(HalconfigProblemBuilder::build)
        .collect(Collectors.toList());

    return new HalconfigProblemSet(problems);
  }
}
