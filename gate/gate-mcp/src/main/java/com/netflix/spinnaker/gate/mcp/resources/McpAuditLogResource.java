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

package com.netflix.spinnaker.gate.mcp.resources;

import com.netflix.spinnaker.gate.mcp.support.McpAuditLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpResource;

/**
 * Read-only view over {@link McpAuditLog}: the mutating tool calls this MCP server itself has made
 * (application create/delete, orchestrations, pipeline triggers/judgments, canary and Managed
 * Delivery writes, ...), most recent first. Lets an MCP client - or an operator inspecting it
 * through one - answer "what has this MCP server actually done" without cross-referencing Gate's
 * general request logs.
 *
 * <p>Entries are process-local and in-memory only (bounded by {@code mcp.server.audit-log-size});
 * see {@link McpAuditLog} for what "recorded" does and doesn't guarantee.
 */
public class McpAuditLogResource {

  private final McpAuditLog auditLog;

  public McpAuditLogResource(McpAuditLog auditLog) {
    this.auditLog = auditLog;
  }

  @McpResource(
      uri = "spinnaker://mcp/audit-log",
      name = "MCP audit log",
      description =
          "Recent mutating actions taken through this MCP server, most recent first, across all applications.")
  public List<Map<String, Object>> auditLog() {
    return toResponse(auditLog.recent(null));
  }

  @McpResource(
      uri = "spinnaker://mcp/audit-log/{application}",
      name = "MCP audit log for an application",
      description =
          "Recent mutating actions taken through this MCP server whose target matched this application/resource "
              + "name, most recent first.")
  public List<Map<String, Object>> auditLogForApplication(
      @McpArg(name = "application", description = "Application name", required = true)
          String application) {
    return toResponse(auditLog.recent(application));
  }

  private static List<Map<String, Object>> toResponse(List<McpAuditLog.Entry> entries) {
    return entries.stream().map(McpAuditLogResource::toMap).collect(Collectors.toList());
  }

  private static Map<String, Object> toMap(McpAuditLog.Entry entry) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("timestamp", entry.timestamp().toString());
    map.put("tool", entry.tool());
    map.put("target", entry.target());
    map.put("user", entry.user());
    return map;
  }
}
