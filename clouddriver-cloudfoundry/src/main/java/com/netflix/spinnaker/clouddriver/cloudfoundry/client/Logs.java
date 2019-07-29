/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static java.util.stream.Collectors.joining;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.DopplerService;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope.EventType;
import org.cloudfoundry.dropsonde.events.LogFactory.LogMessage;

@RequiredArgsConstructor
public class Logs {
  private final DopplerService api;

  public String recentApplicationLogs(String applicationGuid, int instanceIndex) {
    return recentLogsFiltered(applicationGuid, "APP/PROC/WEB", instanceIndex);
  }

  public String recentTaskLogs(String applicationGuid, String taskName) {
    return recentLogsFiltered(applicationGuid, "APP/TASK/" + taskName, 0);
  }

  public List<Envelope> recentLogs(String applicationGuid) {
    return safelyCall(() -> api.recentLogs(applicationGuid))
        .orElseThrow(IllegalStateException::new);
  }

  private String recentLogsFiltered(
      String applicationGuid, String logSourceFilter, int instanceIndex) {
    List<Envelope> envelopes = recentLogs(applicationGuid);

    return envelopes.stream()
        .filter(e -> e.getEventType().equals(EventType.LogMessage))
        .map(Envelope::getLogMessage)
        .filter(
            logMessage ->
                logSourceFilter.equals(logMessage.getSourceType())
                    && logMessage.getSourceInstance().equals(String.valueOf(instanceIndex)))
        .sorted(Comparator.comparingLong(LogMessage::getTimestamp))
        .map(msg -> msg.getMessage().toStringUtf8())
        .collect(joining("\n"));
  }
}
