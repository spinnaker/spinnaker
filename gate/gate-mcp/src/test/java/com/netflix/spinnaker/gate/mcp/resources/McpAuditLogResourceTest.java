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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.gate.mcp.support.McpAuditLog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpAuditLogResourceTest {

  @Test
  void auditLogReturnsAllEntriesAsMaps() {
    McpAuditLog auditLog = new McpAuditLog(10);
    auditLog.record("create_application", "app-a");
    auditLog.record("delete_application", "app-b");
    McpAuditLogResource resource = new McpAuditLogResource(auditLog);

    List<Map<String, Object>> entries = resource.auditLog();

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0))
        .containsEntry("tool", "delete_application")
        .containsEntry("target", "app-b")
        .containsEntry("user", "anonymous")
        .containsKey("timestamp");
  }

  @Test
  void auditLogForApplicationFiltersByTarget() {
    McpAuditLog auditLog = new McpAuditLog(10);
    auditLog.record("create_application", "app-a");
    auditLog.record("delete_application", "app-b");
    McpAuditLogResource resource = new McpAuditLogResource(auditLog);

    List<Map<String, Object>> entries = resource.auditLogForApplication("app-a");

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0)).containsEntry("tool", "create_application");
  }
}
