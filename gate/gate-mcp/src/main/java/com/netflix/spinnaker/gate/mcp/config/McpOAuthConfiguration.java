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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Makes Gate's MCP endpoint usable by OAuth-capable MCP clients (Claude Code, Claude Desktop, MCP
 * Inspector, ...), per the MCP authorization specification:
 *
 * <ul>
 *   <li>an unauthenticated request to the MCP endpoint gets {@code 401} with a {@code
 *       WWW-Authenticate: Bearer resource_metadata="..."} challenge instead of the SSO chain's
 *       browser redirect to the login page;
 *   <li>the RFC 9728 protected-resource metadata document that challenge points at ({@link
 *       com.netflix.spinnaker.gate.mcp.security.McpProtectedResourceMetadataController}) is served
 *       without authentication.
 * </ul>
 *
 * <p>Kept apart from {@link McpServerAutoConfiguration} because it needs Spring Security's {@link
 * HttpSecurity}, which the tool-level context tests don't provide. Only the {@code
 * spring.ai.mcp.server.protocol: STREAMABLE} transport (Gate's default) is covered.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mcp.server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpServerProperties.class)
public class McpOAuthConfiguration {

  @Bean
  public McpResourceUrls mcpResourceUrls(
      McpServerProperties properties,
      @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String mcpEndpoint) {
    return new McpResourceUrls(properties.getExternalUrl(), mcpEndpoint);
  }

  /**
   * Applied by Spring Security to every {@link HttpSecurity} it creates, so the MCP challenge is in
   * place in whichever chain (OAuth2, SAML, x509, API token, ...) ends up handling an MCP request.
   * See {@link McpBearerChallengeFilter} for why this is a filter rather than an entry-point
   * mapping.
   */
  @Bean
  public Customizer<HttpSecurity> mcpBearerChallengeCustomizer(McpResourceUrls urls) {
    McpBearerChallengeFilter filter =
        new McpBearerChallengeFilter(
            PathPatternRequestMatcher.withDefaults().matcher(urls.mcpEndpoint()),
            new McpBearerChallengeEntryPoint(urls));
    return http -> http.addFilterAfter(filter, ExceptionTranslationFilter.class);
  }

  /**
   * Permits anonymous access to the protected-resource metadata, which a client must be able to
   * read before it has a token. Ordered like {@code
   * ManagedDeliverySchemaEndpointConfiguration#schemaSecurityFilterChain}, ahead of the SSO chains.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain mcpProtectedResourceMetadataSecurityFilterChain(
      HttpSecurity http, McpResourceUrls urls) throws Exception {
    PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
    return http.securityMatcher(
            new OrRequestMatcher(
                paths.matcher(McpResourceUrls.METADATA_PATH), paths.matcher(urls.metadataPath())))
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .build();
  }
}
