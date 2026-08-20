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

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;

/**
 * Shared read-only guard used by every mutating MCP tool method; also the point where a successful
 * write is recorded to the {@link McpAuditLog} exposed via the {@code spinnaker://mcp/audit-log}
 * resource, and published to Echo via {@link McpEchoAuditPublisher}.
 */
public class McpAccessGuard {

  private final McpServerProperties properties;
  private final McpAuditLog auditLog;
  private final McpEchoAuditPublisher echoAuditPublisher;

  public McpAccessGuard(McpServerProperties properties) {
    this(properties, new McpAuditLog(properties.getAuditLogSize()));
  }

  public McpAccessGuard(McpServerProperties properties, McpAuditLog auditLog) {
    this(properties, auditLog, McpEchoAuditPublisher.disabled());
  }

  public McpAccessGuard(
      McpServerProperties properties,
      McpAuditLog auditLog,
      McpEchoAuditPublisher echoAuditPublisher) {
    this.properties = properties;
    this.auditLog = auditLog;
    this.echoAuditPublisher = echoAuditPublisher;
  }

  /**
   * Rejects the call if the MCP server is running in read-only mode, without recording an audit
   * entry - for callers (like {@link OrchestrationJobs}) that record their own, richer entry once
   * the operation is confirmed to have completed rather than merely attempted.
   */
  public void checkWriteAccess(String toolName) {
    if (properties.isReadOnly()) {
      throw new McpReadOnlyModeException(toolName);
    }
  }

  /**
   * Rejects the call if the MCP server is running in read-only mode. Every mutating tool method
   * that calls a downstream service directly (not via {@link OrchestrationJobs}) must call this
   * before doing any work.
   */
  public void requireWriteAccess(String toolName) {
    requireWriteAccess(toolName, null);
  }

  /**
   * As {@link #requireWriteAccess(String)}, additionally recording the call - once it's confirmed
   * authorized to proceed - against {@code target} (typically the application, delivery config, or
   * resource id the tool acted on) in the MCP audit log.
   */
  public void requireWriteAccess(String toolName, String target) {
    checkWriteAccess(toolName);
    auditLog.record(toolName, target);
    echoAuditPublisher.publish(toolName, target);
  }
}
