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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers unauthenticated requests to the MCP endpoint with {@code 401 Unauthorized} and a {@code
 * WWW-Authenticate: Bearer resource_metadata="..."} challenge (RFC 6750 / RFC 9728 section 5.1), as
 * the MCP authorization specification requires.
 *
 * <p>Without it, Gate's SSO chains answer with their browser entry point (a 302 to the login page
 * for OAuth2/SAML), which an MCP client can neither follow nor use to discover how to authenticate.
 */
public class McpBearerChallengeEntryPoint implements AuthenticationEntryPoint {

  private final McpResourceUrls urls;

  public McpBearerChallengeEntryPoint(McpResourceUrls urls) {
    this.urls = urls;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) {
    response.setHeader(
        HttpHeaders.WWW_AUTHENTICATE,
        "Bearer resource_metadata=\"" + quotedStringValue(urls.metadataUrl(request)) + "\"");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  /** Escapes a value for use inside an RFC 9110 quoted-string. */
  private static String quotedStringValue(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
