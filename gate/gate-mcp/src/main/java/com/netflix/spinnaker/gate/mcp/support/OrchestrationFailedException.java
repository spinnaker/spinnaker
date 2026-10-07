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

import java.util.List;
import java.util.Map;

/** Thrown when an Orca orchestration submitted by an MCP tool does not complete successfully. */
public class OrchestrationFailedException extends RuntimeException {

  public OrchestrationFailedException(String description, Map<String, ?> task) {
    super(buildMessage(description, task));
  }

  private static String buildMessage(String description, Map<String, ?> task) {
    Object rawTaskId = task == null ? null : task.get("id");
    String taskId = rawTaskId == null ? "unknown" : String.valueOf(rawTaskId);
    String status = task == null ? "unknown" : String.valueOf(task.get("status"));
    String detail = extractExceptionDetail(task);
    StringBuilder message =
        new StringBuilder("Orchestration '")
            .append(description)
            .append("' did not succeed (taskId: ")
            .append(taskId)
            .append(", status: ")
            .append(status)
            .append(")");
    if (detail != null) {
      message.append(": ").append(detail);
    }
    return message.toString();
  }

  @SuppressWarnings("unchecked")
  private static String extractExceptionDetail(Map<String, ?> task) {
    if (task == null) {
      return null;
    }
    Object variables = task.get("variables");
    if (!(variables instanceof List)) {
      return null;
    }
    for (Object entry : (List<Object>) variables) {
      if (!(entry instanceof Map)) {
        continue;
      }
      Map<String, Object> variable = (Map<String, Object>) entry;
      if (!"exception".equals(variable.get("key"))) {
        continue;
      }
      Object value = variable.get("value");
      if (!(value instanceof Map)) {
        continue;
      }
      Object details = ((Map<String, Object>) value).get("details");
      if (!(details instanceof Map)) {
        continue;
      }
      Object errors = ((Map<String, Object>) details).get("errors");
      if (errors instanceof List && !((List<?>) errors).isEmpty()) {
        return String.valueOf(((List<?>) errors).get(0));
      }
    }
    return null;
  }
}
