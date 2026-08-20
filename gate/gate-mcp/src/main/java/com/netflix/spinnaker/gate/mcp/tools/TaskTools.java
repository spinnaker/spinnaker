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

package com.netflix.spinnaker.gate.mcp.tools;

import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

/**
 * MCP tools for inspecting, searching, cancelling, and ad-hoc-creating Orca tasks - the generic
 * unit of work behind every non-pipeline orchestration (application create/delete, one-off deploys,
 * etc; pipeline stages run as tasks internally too, but {@link PipelineTools}/{@link
 * ExecutionTools} are the better fit for those).
 */
public class TaskTools {

  private final TaskService taskService;
  private final OrcaServiceSelector orcaServiceSelector;
  private final McpAccessGuard accessGuard;

  public TaskTools(
      TaskService taskService,
      OrcaServiceSelector orcaServiceSelector,
      McpAccessGuard accessGuard) {
    this.taskService = taskService;
    this.orcaServiceSelector = orcaServiceSelector;
    this.accessGuard = accessGuard;
  }

  @McpTool(
      name = "search_application_tasks",
      description =
          "Search/list an application's tasks (ad-hoc orchestrations such as application create/delete, deploys "
              + "submitted outside of a pipeline, etc.), most recent first. Use this to find a task you created "
              + "earlier with create_task/submit_orchestration, or to audit recent ad-hoc activity for an application.")
  public List<Map<String, Object>> searchApplicationTasks(
      @McpToolParam(description = "Application name", required = true) String application,
      @McpToolParam(
              description =
                  "Comma-delimited list of statuses to filter by, e.g. 'RUNNING,SUCCEEDED'",
              required = false)
          String statuses,
      @McpToolParam(description = "Page number (1-indexed)", required = false) Integer page,
      @McpToolParam(description = "Maximum number of tasks to return", required = false)
          Integer limit) {
    return Retrofit2SyncCall.execute(
        orcaServiceSelector.select().getTasks(application, page, limit, statuses));
  }

  @McpTool(name = "get_task", description = "Retrieve an Orca task by id.")
  public Map<String, Object> getTask(
      @McpToolParam(description = "Task id", required = true) String taskId) {
    return taskService.getTask(taskId);
  }

  @McpTool(
      name = "create_task",
      description =
          "Submit a raw ad-hoc Orca task (the same job-list mechanism as submit_orchestration) and return "
              + "immediately with a task reference, without waiting for it to finish. Use this for long-running or "
              + "fire-and-forget operations; poll completion with get_task. Prefer submit_orchestration when you want "
              + "to block until the result is known.")
  public Map<String, String> createTask(
      @McpToolParam(description = "Application the task runs against", required = true)
          String application,
      @McpToolParam(
              description = "Human-readable description shown in the Spinnaker UI",
              required = true)
          String description,
      @McpToolParam(
              description =
                  "List of job maps to submit, e.g. [{\"type\": \"createServerGroup\", ...}]",
              required = true)
          List<Map<String, Object>> jobs) {
    accessGuard.requireWriteAccess("create_task");

    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("application", application);
    operation.put("description", description);
    operation.put("job", jobs);

    return taskService.createAppTask(application, operation);
  }

  @McpTool(name = "cancel_task", description = "Cancel a running Orca task.")
  public void cancelTask(@McpToolParam(description = "Task id", required = true) String taskId) {
    accessGuard.requireWriteAccess("cancel_task");
    taskService.cancelTask(taskId);
  }
}
