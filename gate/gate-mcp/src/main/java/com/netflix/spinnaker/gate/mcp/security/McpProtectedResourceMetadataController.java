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

package com.netflix.spinnaker.gate.mcp.security;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the RFC 9728 OAuth 2.0 protected-resource metadata for Gate's MCP endpoint, which {@link
 * McpBearerChallengeEntryPoint} points MCP clients at. Served both at the bare well-known path and
 * at the path-suffixed form RFC 9728 section 3.1 derives from the resource URL.
 *
 * <p>Registered by component scanning (Spring MVC only maps {@code @Controller} types), hence the
 * same {@code mcp.server.enabled} condition as the rest of gate-mcp.
 */
@RestController
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true")
public class McpProtectedResourceMetadataController {

  private final McpResourceUrls urls;
  private final McpServerProperties properties;

  public McpProtectedResourceMetadataController(
      McpResourceUrls urls, McpServerProperties properties) {
    this.urls = urls;
    this.properties = properties;
  }

  @GetMapping(
      path = {
        McpResourceUrls.METADATA_PATH,
        McpResourceUrls.METADATA_PATH + "${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}"
      },
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> metadata(HttpServletRequest request) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("resource", urls.resource(request));
    putIfNotEmpty(metadata, "authorization_servers", properties.getAuthorizationServers());
    putIfNotEmpty(metadata, "scopes_supported", properties.getScopesSupported());
    metadata.put("bearer_methods_supported", List.of("header"));
    return metadata;
  }

  private static void putIfNotEmpty(Map<String, Object> metadata, String key, List<String> value) {
    if (value != null && !value.isEmpty()) {
      metadata.put(key, value);
    }
  }
}
