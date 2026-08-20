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

/** Thrown when a mutating MCP tool is invoked while {@code mcp.server.read-only} is true. */
public class McpReadOnlyModeException extends RuntimeException {
  public McpReadOnlyModeException(String toolName) {
    super(
        "Tool '"
            + toolName
            + "' is disabled because the Gate MCP server is running in read-only mode "
            + "(mcp.server.read-only=true). Set mcp.server.read-only=false to allow mutating tools.");
  }
}
