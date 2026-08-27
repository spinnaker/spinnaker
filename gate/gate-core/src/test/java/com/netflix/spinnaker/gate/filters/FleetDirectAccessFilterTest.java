/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.config.FleetConfigurationProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class FleetDirectAccessFilterTest {

  private static final String GLOBAL_URL = "https://spinnaker.example.com";
  private static final String GLOBAL_HOST = "spinnaker.example.com";
  private static final String INSTANCE_HOST = "inst-1.spinnaker.example.com";
  private static final List<GrantedAuthority> USER_ROLE =
      List.of(new SimpleGrantedAuthority("ROLE_USER"));

  private FiatPermissionEvaluator permissionEvaluator;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    permissionEvaluator = mock(FiatPermissionEvaluator.class);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------------
  // Construction / validation
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("a missing global-base-url fails fast with an actionable message")
  void missingGlobalBaseUrlRejected() {
    FleetConfigurationProperties properties = new FleetConfigurationProperties();
    properties.setEnabled(true);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FleetDirectAccessFilter(properties, permissionEvaluator, "/saml/SSO"))
        .withMessageContaining("fleet.global-base-url");
  }

  @Test
  @DisplayName("a global-base-url without scheme or host is rejected")
  void relativeGlobalBaseUrlRejected() {
    FleetConfigurationProperties properties = new FleetConfigurationProperties();
    properties.setEnabled(true);
    properties.setGlobalBaseUrl("/spinnaker");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FleetDirectAccessFilter(properties, permissionEvaluator, "/saml/SSO"))
        .withMessageContaining("absolute URL");
  }

  // ---------------------------------------------------------------------------
  // Cases that must pass through untouched
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("disabled → never redirects, even for a non-admin on the instance host")
  void disabledPassesThrough() throws Exception {
    FleetConfigurationProperties properties = properties();
    properties.setEnabled(false);
    nonAdminSession();

    assertThat(runFilter(properties, request(INSTANCE_HOST, "/applications"))).isNull();
  }

  @Test
  @DisplayName("a request arriving via the global host is left alone")
  void globalHostPassesThrough() throws Exception {
    nonAdminSession();

    assertThat(runFilter(properties(), request(GLOBAL_HOST, "/applications"))).isNull();
  }

  @Test
  @DisplayName("host comparison is case-insensitive")
  void globalHostComparisonIsCaseInsensitive() throws Exception {
    nonAdminSession();

    assertThat(runFilter(properties(), request(GLOBAL_HOST.toUpperCase(), "/applications")))
        .isNull();
  }

  @Test
  @DisplayName("admins may use an instance host directly")
  void adminPassesThrough() throws Exception {
    session("admin");
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(true);

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications"))).isNull();
  }

  @Test
  @DisplayName("OPTIONS preflight is never redirected")
  void optionsPassesThrough() throws Exception {
    nonAdminSession();
    MockHttpServletRequest request = request(INSTANCE_HOST, "/applications");
    request.setMethod("OPTIONS");

    assertThat(runFilter(properties(), request)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/error",
        "/favicon.ico",
        "/health",
        "/health/liveness",
        "/auth/user",
        "/auth/loggedOut",
        "/auth/logout",
        "/plugins/deck/index.js",
        "/webhooks/webhook/git",
        "/notifications/callbacks/slack",
        "/managed/notifications/callbacks/foo",
        "/saml/SSO",
        "/saml2/authenticate/inst-1",
        "/login/saml2/sso/inst-1"
      })
  @DisplayName("default exempt paths are never redirected")
  void exemptPathsPassThrough(String path) throws Exception {
    nonAdminSession();

    assertThat(runFilter(properties(), request(INSTANCE_HOST, path))).isNull();
  }

  @Test
  @DisplayName("a customised saml.login-processing-url is exempt without editing exempt-paths")
  void customSamlAcsIsExempt() throws Exception {
    nonAdminSession();
    MockHttpServletResponse response =
        runFilterForResponse(
            properties(),
            request(INSTANCE_HOST, "/custom/acs"),
            "/custom/acs",
            new MockFilterChain());

    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  @DisplayName("unauthenticated requests fall through to the normal auth chain")
  void unauthenticatedPassesThrough() throws Exception {
    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications"))).isNull();
  }

  @Test
  @DisplayName("anonymous requests fall through to the normal auth chain")
  void anonymousPassesThrough() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications"))).isNull();
  }

  /**
   * Only genuinely stateless machine mechanisms are excluded. Note {@code pre_authenticated} and
   * {@code oauth2} are deliberately NOT in this list: they are what gate-saml / gate-iap and
   * gate-oauth2 produce for real browser logins, so excluding them is what made this guardrail
   * inert for SAML. See the auth-mechanism coverage section below.
   */
  @ParameterizedTest
  @ValueSource(strings = {AuthTypeResolver.TYPE_API_TOKEN, AuthTypeResolver.TYPE_X509})
  @DisplayName("stateless machine auth types are never redirected")
  void machineAuthTypesPassThrough(String authType) throws Exception {
    nonAdminSession();
    MockHttpServletRequest request = request(INSTANCE_HOST, "/applications");
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, authType);

    assertThat(runFilter(properties(), request)).isNull();
  }

  // ---------------------------------------------------------------------------
  // Auth-mechanism coverage
  //
  // The guardrail must cover every *browser* login mechanism Gate supports, because a
  // fleet's whole point is that ordinary users only ever see the global URL. It must NOT
  // cover machine clients, which legitimately address one instance.
  //
  // These use the concrete Authentication implementations each gate module really
  // produces, so a mechanism cannot silently fall out of scope:
  //   gate-basic  BasicAuthProvider           -> UsernamePasswordAuthenticationToken
  //   gate-ldap   formLogin + LDAP provider   -> UsernamePasswordAuthenticationToken
  //   gate-saml   ResponseAuthenticationConverter -> PreAuthenticatedAuthenticationToken
  //   gate-oauth2 OAuth2 filter               -> OAuth2AuthenticationToken (name-matched)
  //   gate-iap    IapAuthenticationFilter     -> PreAuthenticatedAuthenticationToken
  //   gate-x509 / gate-header                 -> PreAuthenticatedAuthenticationToken
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("basic auth: a non-admin browser session is redirected")
  void basicAuthIsCovered() throws Exception {
    sessionAuthentication(new UsernamePasswordAuthenticationToken("alice", "pw", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications")))
        .isEqualTo(GLOBAL_URL + "/applications");
  }

  @Test
  @DisplayName("SAML: a non-admin browser session is redirected")
  void samlIsCovered() throws Exception {
    // gate-saml's ResponseAuthenticationConverter returns a PreAuthenticatedAuthenticationToken,
    // NOT a UsernamePasswordAuthenticationToken. Keying the guardrail on the latter made it
    // silently inert for SAML -- the very mechanism a fleet is built on.
    sessionAuthentication(new PreAuthenticatedAuthenticationToken("alice", "saml", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications")))
        .isEqualTo(GLOBAL_URL + "/applications");
  }

  @Test
  @DisplayName("OAuth2: a non-admin browser session is redirected")
  void oauth2IsCovered() throws Exception {
    sessionAuthentication(new FakeOAuth2AuthenticationToken());
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications")))
        .isEqualTo(GLOBAL_URL + "/applications");
  }

  @Test
  @DisplayName("SAML: an admin is still left alone")
  void samlAdminPassesThrough() throws Exception {
    sessionAuthentication(new PreAuthenticatedAuthenticationToken("admin", "saml", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(true);

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications"))).isNull();
  }

  @Test
  @DisplayName("API tokens are never redirected, even alongside a session")
  void apiTokenIsNotCovered() throws Exception {
    sessionAuthentication(new PreAuthenticatedAuthenticationToken("bot", "token", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    MockHttpServletRequest request = request(INSTANCE_HOST, "/applications");
    request.setAttribute(AuthRequestAttributes.IS_API_TOKEN, Boolean.TRUE);

    assertThat(runFilter(properties(), request)).isNull();
  }

  @Test
  @DisplayName("x509 clients are never redirected")
  void x509IsNotCovered() throws Exception {
    sessionAuthentication(new PreAuthenticatedAuthenticationToken("bot", "cert", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    MockHttpServletRequest request = request(INSTANCE_HOST, "/applications");
    request.setAttribute(AuthTypeResolver.AUTH_TYPE_ATTRIBUTE, AuthTypeResolver.TYPE_X509);

    assertThat(runFilter(properties(), request)).isNull();
  }

  @Test
  @DisplayName("a stateless request with no session is never redirected")
  void statelessRequestIsNotCovered() throws Exception {
    // gate-header and the API-token filter both disable session creation, so a request
    // carrying no session is by definition not a browser we should be steering.
    SecurityContextHolder.getContext()
        .setAuthentication(new PreAuthenticatedAuthenticationToken("svc", "hdr", USER_ROLE));
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);

    assertThat(runFilter(properties(), statelessRequest(INSTANCE_HOST, "/applications"))).isNull();
  }

  // ---------------------------------------------------------------------------
  // The redirect
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "a session-authenticated non-admin on an instance host is redirected to the global URL")
  void nonAdminRedirected() throws Exception {
    nonAdminSession();

    assertThat(runFilter(properties(), request(INSTANCE_HOST, "/applications")))
        .isEqualTo(GLOBAL_URL + "/applications");
  }

  @Test
  @DisplayName("the query string is preserved verbatim")
  void queryStringPreserved() throws Exception {
    nonAdminSession();
    MockHttpServletRequest request = request(INSTANCE_HOST, "/applications");
    request.setQueryString("q=foo%20bar&limit=10");

    assertThat(runFilter(properties(), request))
        .isEqualTo(GLOBAL_URL + "/applications?q=foo%20bar&limit=10");
  }

  @Test
  @DisplayName("an X-Forwarded-Prefix path prefix is preserved in the redirect")
  void forwardedPrefixPreserved() throws Exception {
    nonAdminSession();
    // ForwardedHeaderFilter would have already folded the prefix into the request URI and context
    // path; emulate its result.
    MockHttpServletRequest request = request(INSTANCE_HOST, "/gate/applications");
    request.setContextPath("/gate");

    assertThat(runFilter(properties(), request)).isEqualTo(GLOBAL_URL + "/gate/applications");
  }

  @Test
  @DisplayName("exemptions match the path within the application, ignoring a /gate prefix")
  void exemptionsIgnoreContextPath() throws Exception {
    nonAdminSession();
    MockHttpServletRequest request = request(INSTANCE_HOST, "/gate/saml/SSO");
    request.setContextPath("/gate");

    assertThat(runFilter(properties(), request)).isNull();
  }

  @Test
  @DisplayName("a non-default port on the global URL is carried into the redirect")
  void globalPortPreserved() throws Exception {
    FleetConfigurationProperties properties = properties();
    properties.setGlobalBaseUrl("https://spinnaker.example.com:8443");
    nonAdminSession();

    assertThat(runFilter(properties, request(INSTANCE_HOST, "/applications")))
        .isEqualTo("https://spinnaker.example.com:8443/applications");
  }

  @Test
  @DisplayName("the filter chain is not invoked once a redirect is issued")
  void chainNotInvokedOnRedirect() throws Exception {
    nonAdminSession();
    MockFilterChain chain = new MockFilterChain();
    runFilterForResponse(properties(), request(INSTANCE_HOST, "/applications"), "/saml/SSO", chain);

    assertThat(chain.getRequest()).isNull();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private FleetConfigurationProperties properties() {
    FleetConfigurationProperties properties = new FleetConfigurationProperties();
    properties.setEnabled(true);
    properties.setGlobalBaseUrl(GLOBAL_URL);
    properties.setInstanceId("inst-1");
    return properties;
  }

  /**
   * A browser request: it carries a resolvable server-side session, i.e. the client presented a
   * valid session cookie. That is what distinguishes a browser from a stateless machine client, and
   * it is the same cookie the fleet edge routes on.
   *
   * <p>This is the default for tests in this class so that a case asserting "not redirected" fails
   * loudly if the reason ever becomes "no session" rather than the condition it means to exercise.
   */
  private MockHttpServletRequest request(String host, String uri) {
    MockHttpServletRequest request = statelessRequest(host, uri);
    request.setSession(new MockHttpSession());
    return request;
  }

  /** A request with no server-side session, as a stateless machine client would send. */
  private MockHttpServletRequest statelessRequest(String host, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setScheme("https");
    request.setServerName(host);
    request.setServerPort(443);
    return request;
  }

  /** Puts an authentication in the context, as a completed browser login would. */
  private void sessionAuthentication(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /**
   * Stands in for {@code OAuth2AuthenticationToken} without dragging spring-security-oauth2 into
   * gate-core's test classpath. {@link AuthTypeResolver} classifies OAuth2 by class simple name, so
   * the name is the part that matters here.
   */
  private static final class FakeOAuth2AuthenticationToken extends AbstractAuthenticationToken {
    FakeOAuth2AuthenticationToken() {
      super(USER_ROLE);
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
      return "";
    }

    @Override
    public Object getPrincipal() {
      return "alice";
    }
  }

  private void nonAdminSession() {
    session("alice");
    when(permissionEvaluator.isAdmin(any(Authentication.class))).thenReturn(false);
  }

  private void session(String username) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username, "pw", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  /** Runs the filter and returns the redirect location, or {@code null} if it passed through. */
  private String runFilter(FleetConfigurationProperties properties, MockHttpServletRequest request)
      throws Exception {
    return runFilterForResponse(properties, request, "/saml/SSO", new MockFilterChain())
        .getRedirectedUrl();
  }

  private MockHttpServletResponse runFilterForResponse(
      FleetConfigurationProperties properties,
      MockHttpServletRequest request,
      String samlLoginProcessingUrl,
      MockFilterChain chain)
      throws ServletException, IOException {
    FleetDirectAccessFilter filter =
        new FleetDirectAccessFilter(properties, permissionEvaluator, samlLoginProcessingUrl);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }
}
