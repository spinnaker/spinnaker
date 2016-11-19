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

import com.netflix.spinnaker.halyard.config.model.v1.node.NodeCoordinates;
import lombok.Setter;

public class ProblemBuilder {
  @Setter
  String message;

  @Setter
  String remediation;

  @Setter
  NodeCoordinates coordinates;

  @Setter
  Problem.Severity severity;

  public ProblemBuilder(Problem.Severity severity, String message) {
    this.severity = severity;
    this.message = message;
  }

  public Problem build() {
    return new Problem(severity, coordinates, message, remediation);
  }
}
