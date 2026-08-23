/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.mcp.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.services.internal.EchoService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class McpEchoAuditPublisherTest {

  @Mock private EchoService echoService;

  @Test
  void publishesEventWithToolTargetAndDetailsWhenEchoIsConfigured() {
    when(echoService.postEvent(anyMap())).thenReturn(Calls.response((Void) null));
    McpEchoAuditPublisher publisher = new McpEchoAuditPublisher(Optional.of(echoService));

    publisher.publish("delete_application", "myapp");

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(echoService).postEvent(captor.capture());
    Map<String, Object> event = captor.getValue();

    Map<String, Object> content = (Map<String, Object>) event.get("content");
    assertThat(content)
        .containsEntry("tool", "delete_application")
        .containsEntry("target", "myapp");

    Map<String, Object> details = (Map<String, Object>) event.get("details");
    assertThat(details)
        .containsEntry("source", "mcp")
        .containsEntry("type", "mcp:tool:delete_application")
        .containsEntry("application", "myapp");
    assertThat(event.get("eventId")).isNotNull();
  }

  @Test
  void doesNothingWhenEchoIsNotConfigured() {
    McpEchoAuditPublisher publisher = McpEchoAuditPublisher.disabled();

    publisher.publish("delete_application", "myapp");

    verify(echoService, never()).postEvent(anyMap());
  }

  @Test
  void swallowsExceptionsFromEcho() {
    when(echoService.postEvent(anyMap())).thenThrow(new RuntimeException("echo is down"));
    McpEchoAuditPublisher publisher = new McpEchoAuditPublisher(Optional.of(echoService));

    publisher.publish("delete_application", "myapp");

    verify(echoService).postEvent(anyMap());
  }
}
