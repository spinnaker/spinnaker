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
import lombok.Setter;

public class HalconfigProblemBuilder {
  @Setter
  String message;

  @Setter
  String remediation;

  @Setter
  HalconfigCoordinates coordinates;

  @Setter
  Severity severity;

  public HalconfigProblemBuilder(Severity severity, String message) {
    this.severity = severity;
    this.message = message;
  }

  public HalconfigProblem build() {
    return new HalconfigProblem(severity, coordinates, message, remediation);
  }
}
