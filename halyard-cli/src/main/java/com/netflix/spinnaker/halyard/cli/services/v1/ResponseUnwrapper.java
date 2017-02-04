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

import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem.Severity;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSet;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonEvent;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ResponseUnwrapper<T> {
  private static final Long WAIT_MILLIS = 500L;

  public static <T> T get(DaemonResponse<T> response) {
    formatProblemSet(response.getProblemSet());
    return response.getResponseBody();
  }

  public static <T> T get(DaemonTask<T> task) {
    int eventsSeen = 0;
    while (task.getState() != DaemonTask.State.COMPLETED) {
      eventsSeen = formatEvents(task.getEvents(), eventsSeen);

      try {
        Thread.sleep(WAIT_MILLIS);
      } catch (InterruptedException ignored) {
      }

      task = Daemon.getTask(task.getUuid());
    }

    formatEvents(task.getEvents(), eventsSeen);

    return get(task.getResponse());
  }

  private static int formatEvents(List<DaemonEvent> events, int eventsSeen) {
    for (DaemonEvent event : events.subList(eventsSeen, events.size())) {
      AnsiUi.listItem(event.getMessage());
    }
    return events.size();
  }

  private static void formatProblemSet(ProblemSet problemSet) {
    if (problemSet == null) {
      return;
    }

    Map<String, List<Problem>> locationGroup = problemSet.groupByLocation();
    for (Entry<String, List<Problem>> entry: locationGroup.entrySet()) {

      AnsiUi.location(entry.getKey());
      for (Problem problem : entry.getValue()) {
        Severity severity = problem.getSeverity();
        String message = problem.getMessage();
        String remediation = problem.getRemediation();
        List<String> options = problem.getOptions();

        switch (severity) {
          case FATAL:
          case ERROR:
            AnsiUi.error(message);
            break;
          case WARNING:
            AnsiUi.warning(message);
            break;
          default:
            throw new RuntimeException("Unknown severity level " + severity);
        }

        if (remediation != null && !remediation.isEmpty()) {
          AnsiUi.remediation(remediation);
        }

        if (options != null && !options.isEmpty()) {
          AnsiUi.remediation("Options include: ");
          options.forEach(AnsiUi::listItem);
        }

        // Newline between errors
        AnsiUi.raw("");
      }
    }
  }
}
