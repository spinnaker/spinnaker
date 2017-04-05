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

import com.netflix.spinnaker.halyard.cli.ui.v1.*;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonEvent;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ResponseUnwrapper {
  private static final Long WAIT_MILLIS = 400L;

  public static <C, T> T get(DaemonTask<C, T> task) {
    int lastEvent = 0;

    task = Daemon.getTask(task.getUuid());
    while (!task.getState().isTerminal()) {
      lastEvent = formatEvents(task.getEvents(), lastEvent);

      try {
        Thread.sleep(WAIT_MILLIS);
      } catch (InterruptedException ignored) {
      }

      task = Daemon.getTask(task.getUuid());
    }

    formatEvents(task.getEvents(), lastEvent);
    AnsiSnippet clear = new AnsiSnippet("").setErase(AnsiErase.ERASE_START_LINE);
    AnsiPrinter.print(clear.toString());

    DaemonResponse<T> response = task.getResponse();
    formatProblemSet(response.getProblemSet());
    if (task.getState() == DaemonTask.State.FATAL) {
      Exception fatal = task.getFatalError();
      if (fatal == null) {
        throw new RuntimeException("Task failed without reason. This is a bug.");
      } else {
        throw new ExpectedDaemonFailureException(fatal);
      }
    }

    return response.getResponseBody();
  }

  private static int formatEvents(List<DaemonEvent> events, int lastEvent) {
    for (DaemonEvent event : events.subList(lastEvent, events.size())) {
      formatEvent(event);
    }

    return events.size();
  }

  private static void formatEvent(DaemonEvent event) {
    String stage = event.getStage();
    String message = event.getMessage();
    String detail = event.getDetail();
    AnsiSnippet clear = new AnsiSnippet("").setErase(AnsiErase.ERASE_START_LINE);
    AnsiPrinter.print(clear.toString());

    if (!StringUtils.isEmpty(message)) {
      AnsiSnippet messageSnippet = new AnsiSnippet("- " + message);
      AnsiPrinter.println(messageSnippet.toString());
    }

    if (!StringUtils.isEmpty(detail)) {
      stage = stage + " (" + detail + ")";
    }

    AnsiSnippet stageSnippet = new AnsiSnippet("~ " + stage)
        .addStyle(AnsiStyle.BOLD);
    AnsiPrinter.print(stageSnippet.toString());
  }

  private static void formatProblemSet(ProblemSet problemSet) {
    if (problemSet == null) {
      return;
    }

    AnsiSnippet snippet = new AnsiSnippet("").setErase(AnsiErase.ERASE_START_LINE);
    AnsiPrinter.print(snippet.toString());

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
