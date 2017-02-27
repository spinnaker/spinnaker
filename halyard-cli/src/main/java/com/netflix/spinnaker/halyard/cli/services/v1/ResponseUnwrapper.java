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
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonStage;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonStage.State;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ResponseUnwrapper {
  private static final Long WAIT_MILLIS = 200L;

  public static <C, T> T get(DaemonTask<C, T> task) {
    PrintCoordinates coords = new PrintCoordinates();

    task = Daemon.getTask(task.getUuid());
    while (!task.getState().isTerminal()) {
      coords = formatStages(task.getStages(), coords);

      try {
        Thread.sleep(WAIT_MILLIS);
      } catch (InterruptedException ignored) {
      }

      task = Daemon.getTask(task.getUuid());
    }

    formatStages(task.getStages(), coords);
    AnsiSnippet clear = new AnsiSnippet("").setErase(AnsiErase.ERASE_START_LINE);
    AnsiPrinter.print(clear.toString());

    DaemonResponse<T> response = task.getResponse();
    formatProblemSet(response.getProblemSet());
    if (task.getState() == DaemonTask.State.FATAL) {
      throw new ExpectedDaemonFailureException();
    }

    return response.getResponseBody();
  }

  private static PrintCoordinates formatStages(List<DaemonStage> stages, PrintCoordinates coords) {
    for (DaemonStage stage : stages.subList(coords.getLastStage(), stages.size())) {
      String stageName = stage.getName();
      AnsiSnippet snippet = new AnsiSnippet("~ " + stageName)
          .addStyle(AnsiStyle.BOLD)
          .setErase(AnsiErase.ERASE_START_LINE);
      AnsiPrinter.print(snippet.toString());

      coords = formatEvents(stageName, stage.getEvents(), coords);

      if (stage.getState() == State.INACTIVE) {
        coords.setLastEvent(0);
        coords.setLastStage(coords.getLastStage() + 1);
      }
    }

    return coords;
  }

  private static PrintCoordinates formatEvents(String stageName, List<DaemonEvent> events, PrintCoordinates coords) {
    for (DaemonEvent event : events.subList(coords.getLastEvent(), events.size())) {
      AnsiSnippet snippet = new AnsiSnippet("- " + event.getMessage())
          .setErase(AnsiErase.ERASE_START_LINE);
      AnsiPrinter.println(snippet.toString());

      snippet = new AnsiSnippet("~ " + stageName)
          .addStyle(AnsiStyle.BOLD);
      AnsiPrinter.print(snippet.toString());
    }
    coords.setLastEvent(events.size());
    return coords;
  }

  public static void formatProblemSet(ProblemSet problemSet) {
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

  @Data
  private static class PrintCoordinates {
    int lastStage = 0;
    int lastEvent = 0;
  }
}
