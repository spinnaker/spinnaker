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

import com.netflix.spinnaker.gate.services.TaskService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and submits the generic Orca orchestration payload ({@code {application, description, job:
 * [...]}}) that every mutating Spinnaker operation (application create/delete, deploys,
 * infrastructure upserts, ...) ultimately goes through, then polls for completion.
 *
 * <p>This mirrors the pattern already used by {@code PipelineController.savePipeline} / {@code
 * deletePipeline} in gate-web, centralized here so every MCP tool that submits an orchestration
 * gets the same completion-polling and failure-reporting behavior.
 */
public class OrchestrationJobs {

  private final TaskService taskService;
  private final McpAccessGuard accessGuard;
  private final McpAuditLog auditLog;
  private final McpEchoAuditPublisher echoAuditPublisher;

  public OrchestrationJobs(TaskService taskService, McpAccessGuard accessGuard) {
    this(taskService, accessGuard, new McpAuditLog(500));
  }

  public OrchestrationJobs(
      TaskService taskService, McpAccessGuard accessGuard, McpAuditLog auditLog) {
    this(taskService, accessGuard, auditLog, McpEchoAuditPublisher.disabled());
  }

  public OrchestrationJobs(
      TaskService taskService,
      McpAccessGuard accessGuard,
      McpAuditLog auditLog,
      McpEchoAuditPublisher echoAuditPublisher) {
    this.taskService = taskService;
    this.accessGuard = accessGuard;
    this.auditLog = auditLog;
    this.echoAuditPublisher = echoAuditPublisher;
  }

  /**
   * Rejects the call if the MCP server is running in read-only mode. Every mutating tool method
   * must call this before doing any work. Doesn't itself record an audit entry - {@link #submit}
   * does, once the orchestration is confirmed to have succeeded.
   */
  public void requireWriteAccess(String toolName) {
    accessGuard.checkWriteAccess(toolName);
  }

  public Map<String, Object> submit(
      String toolName, String application, String description, List<Map<String, Object>> jobs) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("application", application);
    operation.put("description", description);
    operation.put("job", jobs);

    Map<String, ?> result = taskService.createAndWaitForCompletion(operation);
    Object status = result == null ? null : result.get("status");
    if (!"SUCCEEDED".equalsIgnoreCase(String.valueOf(status))) {
      throw new OrchestrationFailedException(description, result);
    }
    auditLog.record(toolName, application);
    echoAuditPublisher.publish(toolName, application);
    @SuppressWarnings("unchecked")
    Map<String, Object> typedResult = (Map<String, Object>) result;
    return typedResult;
  }
}
