/*
 * Copyright 2026 Spinnaker.io
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

package com.netflix.spinnaker.gate.mcp.config;

import com.netflix.spinnaker.gate.mcp.security.McpBearerChallengeEntryPoint;
import com.netflix.spinnaker.gate.mcp.security.McpBearerChallengeFilter;
import com.netflix.spinnaker.gate.mcp.security.McpResourceUrls;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Adds the MCP bearer challenge filter to all active Gate security chains. */
public class McpBearerChallengeConfigurer
    extends AbstractHttpConfigurer<McpBearerChallengeConfigurer, HttpSecurity> {

  @Override
  public void configure(HttpSecurity http) {
    ApplicationContext context = http.getSharedObject(ApplicationContext.class);
    if (context == null) {
      return;
    }

    McpResourceUrls urls = context.getBeanProvider(McpResourceUrls.class).getIfAvailable();
    if (urls != null) {
      http.addFilterAfter(
          new McpBearerChallengeFilter(
              PathPatternRequestMatcher.withDefaults().matcher(urls.mcpEndpoint()),
              new McpBearerChallengeEntryPoint(urls)),
          ExceptionTranslationFilter.class);
    }
  }
}
