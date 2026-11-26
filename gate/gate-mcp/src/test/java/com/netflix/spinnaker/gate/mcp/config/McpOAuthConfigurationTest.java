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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.gate.mcp.security.McpProtectedResourceMetadataController;
import com.netflix.spinnaker.gate.mcp.security.McpResourceUrls;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Runs requests through a real Spring Security filter chain shaped like Gate's SSO chains (every
 * request authenticated, a browser login entry point) to prove that, once gate-mcp is enabled, an
 * unauthenticated MCP client gets a {@code 401} bearer challenge it can act on, the RFC 9728
 * metadata is readable anonymously, and nothing changes for requests outside the MCP endpoint.
 */
class McpOAuthConfigurationTest {

  private static final String CHALLENGE =
      "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\"";

  private final WebApplicationContextRunner baseRunner =
      new WebApplicationContextRunner()
          .withUserConfiguration(
              SsoLikeSecurity.class,
              FakeEndpoints.class,
              McpOAuthConfiguration.class,
              McpProtectedResourceMetadataController.class);

  private final WebApplicationContextRunner runner =
      baseRunner.withPropertyValues("mcp.server.enabled=true");

  @Test
  void unauthenticatedMcpRequestGetsBearerChallengeInsteadOfLoginRedirect() {
    runner.run(
        context ->
            mockMvc(context)
                .perform(mcpPost("/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION)));
  }

  @Test
  void withoutMcpEnabledTheSameRequestIsRedirectedToLogin() {
    baseRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(McpResourceUrls.class);
          assertThat(context).doesNotHaveBean(McpProtectedResourceMetadataController.class);
          mockMvc(context)
              .perform(mcpPost("/mcp"))
              .andExpect(status().is3xxRedirection())
              .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        });
  }

  @Test
  void responsesOutsideTheMcpEndpointAreUnchanged() {
    List<RequestBuilder> requests =
        List.of(
            get("/applications"),
            get("/applications").header("X-Requested-With", "XMLHttpRequest"),
            get("/applications").accept(MediaType.APPLICATION_JSON),
            post("/applications").contentType(MediaType.APPLICATION_JSON).content("{}"));
    for (RequestBuilder request : requests) {
      MockHttpServletResponse[] baseline = new MockHttpServletResponse[1];
      baseRunner.run(context -> baseline[0] = perform(context, request));
      runner.run(
          context -> {
            MockHttpServletResponse withMcp = perform(context, request);
            assertThat(withMcp.getStatus()).isEqualTo(baseline[0].getStatus());
            assertThat(withMcp.getHeader(HttpHeaders.LOCATION))
                .isEqualTo(baseline[0].getHeader(HttpHeaders.LOCATION));
            assertThat(withMcp.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(baseline[0].getHeader(HttpHeaders.WWW_AUTHENTICATE));
          });
    }
  }

  @Test
  void authenticatedMcpRequestReachesTheEndpoint() {
    runner.run(
        context ->
            mockMvc(context)
                .perform(mcpPost("/mcp").header(HeaderUserFilter.HEADER, "alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("mcp")));
  }

  @Test
  void authenticatedButUnauthorizedMcpRequestIsStillForbidden() {
    runner.run(
        context ->
            mockMvc(context)
                .perform(mcpPost("/mcp").header(HeaderUserFilter.HEADER, "bob"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)));
  }

  @Test
  void metadataIsServedAnonymouslyAtBothWellKnownPaths() {
    runner.run(
        context -> {
          MockMvc mockMvc = mockMvc(context);
          for (String path :
              List.of(
                  "/.well-known/oauth-protected-resource",
                  "/.well-known/oauth-protected-resource/mcp")) {
            mockMvc
                .perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resource").value("http://localhost/mcp"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.authorization_servers").doesNotExist())
                .andExpect(jsonPath("$.scopes_supported").doesNotExist());
          }
        });
  }

  @Test
  void configuredExternalUrlAuthorizationServersAndScopesArePublished() {
    runner
        .withPropertyValues(
            "mcp.server.external-url=https://gate.example.com/api/",
            "mcp.server.authorization-servers[0]=https://login.example.com/oauth2/default",
            "mcp.server.scopes-supported=openid,email")
        .run(
            context -> {
              MockMvc mockMvc = mockMvc(context);
              mockMvc
                  .perform(mcpPost("/mcp"))
                  .andExpect(status().isUnauthorized())
                  .andExpect(
                      header()
                          .string(
                              HttpHeaders.WWW_AUTHENTICATE,
                              "Bearer resource_metadata=\"https://gate.example.com/api"
                                  + "/.well-known/oauth-protected-resource/mcp\""));
              mockMvc
                  .perform(get("/.well-known/oauth-protected-resource/mcp"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.resource").value("https://gate.example.com/api/mcp"))
                  .andExpect(
                      jsonPath("$.authorization_servers[0]")
                          .value("https://login.example.com/oauth2/default"))
                  .andExpect(jsonPath("$.scopes_supported[0]").value("openid"))
                  .andExpect(jsonPath("$.scopes_supported[1]").value("email"));
            });
  }

  @Test
  void customMcpEndpointPathIsHonoured() {
    runner
        .withPropertyValues("spring.ai.mcp.server.streamable-http.mcp-endpoint=/ai/mcp")
        .run(
            context -> {
              MockMvc mockMvc = mockMvc(context);
              mockMvc
                  .perform(mcpPost("/ai/mcp"))
                  .andExpect(status().isUnauthorized())
                  .andExpect(
                      header()
                          .string(
                              HttpHeaders.WWW_AUTHENTICATE,
                              "Bearer resource_metadata=\"http://localhost"
                                  + "/.well-known/oauth-protected-resource/ai/mcp\""));
              mockMvc
                  .perform(get("/.well-known/oauth-protected-resource/ai/mcp"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.resource").value("http://localhost/ai/mcp"));
              mockMvc.perform(mcpPost("/mcp")).andExpect(status().is3xxRedirection());
            });
  }

  private static MockHttpServletRequestBuilder mcpPost(String path) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
  }

  private static MockHttpServletResponse perform(
      WebApplicationContext context, RequestBuilder request) throws Exception {
    return mockMvc(context).perform(request).andReturn().getResponse();
  }

  private static MockMvc mockMvc(WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
        .build();
  }

  /**
   * Stands in for Gate's OAuth2/SAML chains: everything authenticated, a browser login entry point
   * registered through the same {@code ExceptionHandlingConfigurer} mechanism {@code oauth2Login()}
   * uses, and an extra role rule on {@code /mcp} to exercise the authenticated-but-denied path.
   */
  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class SsoLikeSecurity {
    @Bean
    @Order(3)
    SecurityFilterChain ssoLikeSecurityFilterChain(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(
              requests ->
                  requests.requestMatchers("/mcp").hasRole("MCP").anyRequest().authenticated())
          .formLogin(Customizer.withDefaults())
          .csrf(AbstractHttpConfigurer::disable)
          .addFilterBefore(new HeaderUserFilter(), AnonymousAuthenticationFilter.class)
          .build();
    }
  }

  /** Authenticates {@code alice} (with ROLE_MCP) or {@code bob} (no roles) from a test header. */
  static class HeaderUserFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Test-User";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      String user = request.getHeader(HEADER);
      if (user != null) {
        List<GrantedAuthority> authorities =
            "alice".equals(user) ? List.of(new SimpleGrantedAuthority("ROLE_MCP")) : List.of();
        SecurityContextHolder.getContext()
            .setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, "n/a", authorities));
      }
      filterChain.doFilter(request, response);
    }
  }

  @RestController
  static class FakeEndpoints {
    @PostMapping({"/mcp", "/ai/mcp"})
    String mcp() {
      return "mcp";
    }

    @GetMapping("/applications")
    String listApplications() {
      return "[]";
    }

    @PostMapping("/applications")
    String createApplication() {
      return "{}";
    }
  }
}
