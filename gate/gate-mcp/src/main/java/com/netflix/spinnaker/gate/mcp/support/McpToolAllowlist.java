/*
 * Copyright 2026 Wise, PLC.
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

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Restricts the tools registered with the MCP server to those named in {@code
 * mcp.server.allowed-tools}.
 *
 * <p>Spring AI turns every {@code @McpTool} method into a tool specification and publishes them as
 * {@code List<...ToolSpecification>} beans, which its MCP server auto-configuration then registers
 * wholesale. This post-processor filters those lists before the server consumes them, so a tool
 * that isn't allowlisted is never registered: it doesn't appear in {@code tools/list} and can't be
 * called. When no allowlist is configured, it does nothing.
 */
public class McpToolAllowlist implements BeanPostProcessor, SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(McpToolAllowlist.class);

  private final Set<String> allowedTools;
  private final Set<String> registeredTools = ConcurrentHashMap.newKeySet();

  /**
   * @param allowedTools tool names to expose, or {@code null} to expose every tool
   */
  public McpToolAllowlist(Collection<String> allowedTools) {
    this.allowedTools = allowedTools == null ? null : Set.copyOf(allowedTools);
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (allowedTools == null || !(bean instanceof List<?> specifications)) {
      return bean;
    }
    if (specifications.stream().allMatch(specification -> toolName(specification) == null)) {
      return bean;
    }

    List<Object> filtered = new ArrayList<>();
    Set<String> excluded = new TreeSet<>();
    for (Object specification : specifications) {
      String name = toolName(specification);
      if (name != null) {
        registeredTools.add(name);
      }
      if (name == null || allowedTools.contains(name)) {
        filtered.add(specification);
      } else {
        excluded.add(name);
      }
    }
    if (!excluded.isEmpty()) {
      log.info("Excluding MCP tools not in mcp.server.allowed-tools: {}", excluded);
    }
    return filtered;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (allowedTools == null) {
      return;
    }
    Set<String> unknown = new TreeSet<>(allowedTools);
    unknown.removeAll(registeredTools);
    if (!unknown.isEmpty()) {
      log.warn(
          "mcp.server.allowed-tools lists tools that are not registered (misspelled, renamed, or"
              + " backed by a disabled service): {}",
          unknown);
    }
  }

  private static String toolName(Object specification) {
    McpSchema.Tool tool = null;
    if (specification instanceof McpServerFeatures.SyncToolSpecification s) {
      tool = s.tool();
    } else if (specification instanceof McpServerFeatures.AsyncToolSpecification s) {
      tool = s.tool();
    } else if (specification instanceof McpStatelessServerFeatures.SyncToolSpecification s) {
      tool = s.tool();
    } else if (specification instanceof McpStatelessServerFeatures.AsyncToolSpecification s) {
      tool = s.tool();
    }
    return tool == null ? null : tool.name();
  }
}
