/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.DopplerService;
import java.util.Arrays;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope.EventType;
import org.cloudfoundry.dropsonde.events.LogFactory.LogMessage;
import org.cloudfoundry.dropsonde.events.LogFactory.LogMessage.MessageType;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

class LogsTest {
  private Envelope logMessage(
      long timestamp, String message, String sourceType, int sourceInstance) {
    return envelope(EventType.LogMessage, timestamp, message, sourceType, sourceInstance);
  }

  private Envelope envelope(
      EventType eventType, long timestamp, String message, String sourceType, int sourceInstance) {
    return Envelope.newBuilder(Envelope.getDefaultInstance())
        .setEventType(eventType)
        .setLogMessage(
            LogMessage.newBuilder()
                .setMessageType(MessageType.OUT)
                .setTimestamp(timestamp)
                .setMessage(ByteString.copyFrom(message, UTF_8))
                .setSourceType(sourceType)
                .setSourceInstance(String.valueOf(sourceInstance))
                .build())
        .setOrigin("")
        .build();
  }

  private DopplerService fakeDopplerService(String forAppGuid, Envelope... envelopes) {
    DopplerService dopplerService = mock(DopplerService.class);
    when(dopplerService.recentLogs(eq(forAppGuid)))
        .thenReturn(Calls.response(Response.success(Arrays.asList(envelopes))));
    return dopplerService;
  }

  @Test
  void recentTaskLogs_filterInLogMessagesOnly() {
    Logs logs = new Logs(fakeDopplerService("12345", envelope(EventType.Error, 0, "", "", 0)));
    String result = logs.recentTaskLogs("12345", "task1");
    assertThat(result).isEmpty();
  }

  @Test
  void recentTaskLogs_filterInSpecifiedTaskLogMessagesOnly() {
    Logs logs =
        new Logs(
            fakeDopplerService(
                "12345",
                logMessage(0, "msg1", "APP/TASK/task1", 0),
                logMessage(0, "msg2", "APP/TASK/task2", 0)));

    String result = logs.recentTaskLogs("12345", "task1");
    assertThat(result).isEqualTo("msg1");
  }

  @Test
  void recentTaskLogs_returnsSortedLogMessagesByTimestampsAsc() {
    Logs logs =
        new Logs(
            fakeDopplerService(
                "12345",
                logMessage(10, "msg1", "APP/TASK/task1", 0),
                logMessage(1, "msg2", "APP/TASK/task1", 0)));

    String[] result = logs.recentTaskLogs("12345", "task1").split("\n");
    assertThat(result.length).isEqualTo(2);
    assertThat(result[0]).isEqualTo("msg2");
    assertThat(result[1]).isEqualTo("msg1");
  }

  @Test
  void recentApplicationLogs_filterInAppLogMessagesOnlyForSpecifiedAppGuid() {
    DopplerService dopplerService = mock(DopplerService.class);
    when(dopplerService.recentLogs(eq("12345")))
        .thenReturn(
            Calls.response(
                Response.success(singletonList(logMessage(0, "msg1", "APP/PROC/WEB", 0)))));
    when(dopplerService.recentLogs(eq("99999")))
        .thenReturn(
            Calls.response(
                Response.success(singletonList(logMessage(0, "msg2", "APP/PROC/WEB", 0)))));

    Logs logs = new Logs(dopplerService);

    String result = logs.recentApplicationLogs("12345", 0);
    assertThat(result).isEqualTo("msg1");
  }

  @Test
  void recentApplicationLogs_filterInAppLogMessagesOnlyForSpecifiedAppGuidAndSourceInstanceIndex() {
    Logs logs =
        new Logs(
            fakeDopplerService(
                "12345",
                logMessage(0, "msg1", "APP/PROC/WEB", 0),
                logMessage(0, "msg2", "APP/PROC/WEB", 1)));

    String result = logs.recentApplicationLogs("12345", 0);
    assertThat(result).isEqualTo("msg1");
  }
}
