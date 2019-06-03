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
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import java.util.List;
import lombok.Setter;

public class ConfigProblemBuilder {
  @Setter private Severity severity;

  @Setter private Node node;

  @Setter private String message;

  @Setter private String remediation;

  @Setter private List<String> options;

  public ConfigProblemBuilder(Severity severity, String message) {
    this.severity = severity;
    this.message = message;
  }

  public Problem build() {
    String location = "Global";
    if (node != null) {
      location = node.getNameToRoot();
    }

    return new Problem(message, remediation, options, severity, location);
  }
}
