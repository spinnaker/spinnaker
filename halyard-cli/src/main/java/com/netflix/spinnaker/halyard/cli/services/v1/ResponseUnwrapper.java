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

package com.netflix.spinnaker.halyard.cli.services.v1;

import com.netflix.spinnaker.halyard.DaemonResponse;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;

public class ResponseUnwrapper<T> {
  public static <T> T get(DaemonResponse<T> response) {
    formatProblemSet(response.getProblemSet());
    return response.getResponseBody();
  }

  private static void formatProblemSet(ProblemSet problemSet) {
    if (problemSet == null) {
      return;
    }

    problemSet.sortIncreasingSeverity();
    for (Problem problem : problemSet.getProblems()) {
      Severity severity = problem.getSeverity();
      String problemLocation = problem.getReferenceTitle();
      String message = problem.getMessage();
      String remediation = problem.getRemediation();

      switch(severity) {
        case FATAL:
        case ERROR:
          AnsiUi.error(problemLocation);
          AnsiUi.error(message);
          break;
        case WARNING:
          AnsiUi.warning(problemLocation);
          AnsiUi.warning(message);
          break;
        default:
          throw new RuntimeException("Unknown severity level " + severity);
      }

      if (remediation != null && !remediation.isEmpty()) {
        AnsiUi.remediation(remediation);
      }

      // Newline between errors
      AnsiUi.raw("");
    }
  }
}
