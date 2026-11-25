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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds the externally visible URLs of the MCP endpoint (the OAuth 2.0 protected resource) and of
 * its RFC 9728 protected-resource metadata document.
 *
 * <p>When {@code mcp.server.external-url} is set it is used verbatim. Otherwise the base URL is
 * derived from the incoming request, which reflects {@code X-Forwarded-*} headers only when Gate is
 * configured to honour them.
 */
public class McpResourceUrls {

  /** RFC 9728 section 3 well-known path prefix. */
  public static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  private final String externalUrl;
  private final String mcpEndpoint;

  /**
   * @param externalUrl public base URL of Gate, or {@code null}/blank to derive it per request
   * @param mcpEndpoint path of the MCP endpoint relative to Gate's context path, e.g. {@code /mcp}
   */
  public McpResourceUrls(String externalUrl, String mcpEndpoint) {
    this.externalUrl =
        externalUrl == null || externalUrl.isBlank() ? null : stripTrailingSlash(externalUrl);
    this.mcpEndpoint = mcpEndpoint.startsWith("/") ? mcpEndpoint : "/" + mcpEndpoint;
  }

  /** Path of the MCP endpoint relative to Gate's context path. */
  public String mcpEndpoint() {
    return mcpEndpoint;
  }

  /** Path of the metadata document for the MCP endpoint, relative to Gate's context path. */
  public String metadataPath() {
    return METADATA_PATH + mcpEndpoint;
  }

  /** The protected-resource identifier: the absolute URL of the MCP endpoint. */
  public String resource(HttpServletRequest request) {
    return baseUrl(request) + mcpEndpoint;
  }

  /** Absolute URL of the protected-resource metadata document. */
  public String metadataUrl(HttpServletRequest request) {
    return baseUrl(request) + metadataPath();
  }

  private String baseUrl(HttpServletRequest request) {
    if (externalUrl != null) {
      return externalUrl;
    }
    return stripTrailingSlash(
        ServletUriComponentsBuilder.fromContextPath(request).build().toUriString());
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
