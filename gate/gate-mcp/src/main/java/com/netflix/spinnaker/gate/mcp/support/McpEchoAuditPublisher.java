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

import com.netflix.spinnaker.gate.services.internal.EchoService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes every mutating MCP tool call to Echo's generic event endpoint ({@code
 * EchoService.postEvent}), the same mechanism Gate's own {@code PipelineService.triggerViaEcho}
 * uses for manually-triggered pipelines - so an MCP-initiated write shows up alongside every other
 * write Spinnaker already feeds to Echo (notifications, audit UIs, and anything else subscribed to
 * Echo's event stream), rather than only being visible in gate-mcp's own {@link McpAuditLog}.
 *
 * <p>Echo is an optional Spinnaker service ({@code services.echo.enabled}); when it isn't
 * configured, or a publish attempt fails, this quietly no-ops rather than failing the tool call -
 * an MCP write should never be blocked or rolled back because the audit trail couldn't be recorded.
 */
public class McpEchoAuditPublisher {

  private static final Logger log = LoggerFactory.getLogger(McpEchoAuditPublisher.class);

  private final Optional<EchoService> echoService;

  public McpEchoAuditPublisher(Optional<EchoService> echoService) {
    this.echoService = echoService;
  }

  public static McpEchoAuditPublisher disabled() {
    return new McpEchoAuditPublisher(Optional.empty());
  }

  /**
   * Best-effort publish of a completed/authorized MCP write to Echo. {@code target} is typically
   * the application, delivery config, or resource/execution/task id the tool acted on - not always
   * literally an application name, but included as {@code details.application} regardless since
   * that's the field Echo's existing consumers (notifications, filters) key on.
   */
  public void publish(String tool, String target) {
    if (echoService.isEmpty()) {
      return;
    }

    String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");

    Map<String, Object> content = new LinkedHashMap<>();
    content.put("tool", tool);
    content.put("target", target);
    content.put("user", user);

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("source", "mcp");
    details.put("type", "mcp:tool:" + tool);
    if (target != null) {
      details.put("application", target);
    }

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("content", content);
    event.put("details", details);
    event.put("eventId", UUID.randomUUID().toString());

    try {
      Retrofit2SyncCall.execute(echoService.get().postEvent(event));
    } catch (Exception e) {
      log.warn("Failed to publish MCP audit event to Echo for tool '{}'", tool, e);
    }
  }
}
