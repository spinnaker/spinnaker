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

import java.util.List;
import org.junit.jupiter.api.Test;

class McpAuditLogTest {

  @Test
  void recentReturnsEntriesMostRecentFirst() {
    McpAuditLog auditLog = new McpAuditLog(10);

    auditLog.record("create_application", "app-a");
    auditLog.record("delete_application", "app-b");

    List<McpAuditLog.Entry> entries = auditLog.recent(null);

    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).tool()).isEqualTo("delete_application");
    assertThat(entries.get(1).tool()).isEqualTo("create_application");
  }

  @Test
  void recentFiltersByTargetCaseInsensitively() {
    McpAuditLog auditLog = new McpAuditLog(10);

    auditLog.record("create_application", "app-a");
    auditLog.record("delete_application", "app-b");

    List<McpAuditLog.Entry> entries = auditLog.recent("APP-A");

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).tool()).isEqualTo("create_application");
  }

  @Test
  void evictsOldestEntriesBeyondCapacity() {
    McpAuditLog auditLog = new McpAuditLog(2);

    auditLog.record("tool_one", "target-1");
    auditLog.record("tool_two", "target-2");
    auditLog.record("tool_three", "target-3");

    List<McpAuditLog.Entry> entries = auditLog.recent(null);

    assertThat(entries).hasSize(2);
    assertThat(entries)
        .extracting(McpAuditLog.Entry::tool)
        .containsExactly("tool_three", "tool_two");
  }

  @Test
  void recordsAnonymousUserWhenNoAuthenticatedRequestContext() {
    McpAuditLog auditLog = new McpAuditLog(10);

    auditLog.record("create_application", "app-a");

    assertThat(auditLog.recent(null).get(0).user()).isEqualTo("anonymous");
  }
}
