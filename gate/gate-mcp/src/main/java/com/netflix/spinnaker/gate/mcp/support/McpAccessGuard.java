/*
 * Copyright 2026 Netflix, Inc.
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

/** Shared read-only guard used by every mutating MCP tool method. */
public class McpAccessGuard {

  private final McpServerProperties properties;

  public McpAccessGuard(McpServerProperties properties) {
    this.properties = properties;
  }

  /**
   * Rejects the call if the MCP server is running in read-only mode. Every mutating tool method
   * must call this before doing any work.
   */
  public void requireWriteAccess(String toolName) {
    if (properties.isReadOnly()) {
      throw new McpReadOnlyModeException(toolName);
    }
  }
}
