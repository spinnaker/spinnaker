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

package com.netflix.spinnaker.gate.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Gate's Model Context Protocol (MCP) server.
 *
 * <p>Disabled by default: an operator must explicitly set {@code mcp.server.enabled: true} to
 * expose the MCP endpoint, and {@code mcp.server.read-only: false} to allow the mutating tools
 * (application create/delete, orchestration submission, pipeline triggers, manual judgment, etc.)
 * to actually execute rather than reject.
 */
@Data
@ConfigurationProperties("mcp.server")
public class McpServerProperties {
  private boolean enabled = false;
  private boolean readOnly = true;

  /**
   * Number of most-recent mutating-tool invocations to retain in the in-memory {@code
   * spinnaker://mcp/audit-log} resource. Oldest entries are dropped once this is exceeded.
   */
  private int auditLogSize = 500;

  /**
   * When true (the default) and Echo is configured ({@code services.echo.enabled: true}), every
   * mutating tool call is also published as an event to Echo - the same audit/notification stream
   * every other Spinnaker write already feeds - in addition to being recorded in the in-memory
   * {@code spinnaker://mcp/audit-log}. Set to false to keep MCP writes out of Echo's event stream
   * while still recording them locally.
   */
  private boolean auditEchoEvents = true;
}
