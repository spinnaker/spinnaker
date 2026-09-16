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

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory, bounded record of every mutating action taken through an MCP tool - the write-side
 * counterpart to Gate's normal request logging, scoped specifically to actions an MCP client
 * initiated (as opposed to Deck or a direct REST caller, neither of which go through this class).
 *
 * <p>Entries for tools that submit an Orca orchestration ({@link OrchestrationJobs#submit}) are
 * recorded only after Orca reports the orchestration {@code SUCCEEDED}, so they reflect a confirmed
 * outcome. Entries for tools that call a downstream service directly ({@link
 * McpAccessGuard#requireWriteAccess(String, String)}) are recorded once the read-only gate passes
 * and before the downstream call is made, so they reflect an authorized attempt rather than a
 * guaranteed success - the downstream call could still fail or be rejected by Fiat afterward.
 *
 * <p>This log is process-local: in a multi-instance Gate deployment, each instance only holds the
 * entries it personally handled, and the log is lost on restart. It exists to answer "what has this
 * MCP server done recently" for an operator or MCP client during a session, not as a durable audit
 * trail - see the README's recommended follow-on work for a persistent alternative.
 */
public class McpAuditLog {

  private final int capacity;
  private final Deque<Entry> entries = new ArrayDeque<>();

  public McpAuditLog(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  public synchronized void record(String tool, String target) {
    entries.addFirst(
        new Entry(
            Instant.now(),
            tool,
            target,
            AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")));
    while (entries.size() > capacity) {
      entries.removeLast();
    }
  }

  /**
   * Returns recorded entries, most recent first, optionally filtered to those whose {@code target}
   * matches (case-insensitively) the given application/resource identifier.
   */
  public synchronized List<Entry> recent(String target) {
    return entries.stream()
        .filter(entry -> target == null || target.equalsIgnoreCase(entry.target()))
        .collect(Collectors.toList());
  }

  public record Entry(Instant timestamp, String tool, String target, String user) {}
}
