/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.Rule;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import com.netflix.spinnaker.gate.security.oauth2.SpinnakerOAuth2User;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.security.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Exercises the real filter, limiter and principal provider against a real redis. The filter runs
 * after the spring security filter chain, so these tests establish the {@link Authentication} each
 * authentication mechanism leaves behind and check the filter derives the right principal from it.
 */
@SuppressWarnings("deprecation") // kork's User is the principal type all of gate's auth modes use
class RateLimitingFilterTest {

  static EmbeddedRedis embeddedRedis;
  static JedisPool pool;

  RateLimiterConfiguration config;
  List<RequestIdentityExtractor> extractors = new ArrayList<>();

  /** What the filter chain did with the request. */
  record Result(MockHttpServletResponse response, boolean passedThrough) {}

  @BeforeAll
  static void startRedis() {
    embeddedRedis = EmbeddedRedis.embed();
    pool = (JedisPool) embeddedRedis.getPool();
  }

  @AfterAll
  static void stopRedis() {
    embeddedRedis.destroy();
  }

  @BeforeEach
  void setup() {
    try (Jedis jedis = pool.getResource()) {
      jedis.flushDB();
    }
    config = new RateLimiterConfiguration();
    config.setLearning(false);
    config.setCapacity(3);
    config.setRateSeconds(60);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  RateLimitingFilter filter() {
    return new RateLimitingFilter(
        new RedisRateLimiter(pool),
        new NoopRegistry(),
        new RedisRateLimitPrincipalProvider(pool, config),
        new DefaultRateLimitPrincipalResolver(extractors),
        new RateLimitRuleMatcher(config.getRules(), config.getExcludePathPatterns()));
  }

  Result call(RateLimitingFilter filter, String method, String uri) throws Exception {
    return call(filter, method, uri, request -> {});
  }

  Result call(
      RateLimitingFilter filter, String method, String uri, Consumer<MockHttpServletRequest> setup)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr("10.0.0.1");
    setup.accept(request);
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] passedThrough = {false};
    FilterChain chain = (req, res) -> passedThrough[0] = true;

    filter.doFilter(request, response, chain);
    return new Result(response, passedThrough[0]);
  }

  static void authenticateAs(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  static User korkUser(String username) {
    User user = new User();
    user.setUsername(username);
    user.setEmail(username);
    return user;
  }

  static Authentication preAuthenticated(String username) {
    return new PreAuthenticatedAuthenticationToken(korkUser(username), "N/A", List.of());
  }

  long requestsCounted(String principal) {
    try (Jedis jedis = pool.getResource()) {
      String count = jedis.get("rateLimit:" + principal);
      return count == null ? 0 : Long.parseLong(count);
    }
  }

  @Test
  void rejectsOverLimitRequestsWithRetryAfterAndAJsonBody() throws Exception {
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    for (int remaining = 2; remaining >= 0; remaining--) {
      Result result = call(filter, "GET", "/applications");
      assertThat(result.passedThrough()).isTrue();
      assertThat(result.response().getStatus()).isEqualTo(200);
      assertThat(result.response().getHeader("X-RateLimit-Capacity")).isEqualTo("3");
      assertThat(result.response().getHeader("X-RateLimit-Remaining"))
          .isEqualTo(Integer.toString(remaining));
      assertThat(result.response().getHeader("X-RateLimit-Learning")).isEqualTo("false");
      assertThat(result.response().getHeader("Retry-After")).isNull();
    }

    Result rejected = call(filter, "GET", "/applications");

    assertThat(rejected.passedThrough()).isFalse();
    MockHttpServletResponse response = rejected.response();
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(Integer.parseInt(response.getHeader("Retry-After"))).isBetween(1, 60);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .contains("\"message\": \"Rate capacity exceeded\"")
        .contains("\"retryAfterSeconds\": " + response.getHeader("Retry-After"));
    assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

    ZonedDateTime reset =
        ZonedDateTime.parse(
            response.getHeader("X-RateLimit-Reset"), DateTimeFormatter.RFC_1123_DATE_TIME);
    assertThat(reset.toInstant()).isAfter(Instant.now().minusSeconds(1));
    assertThat(reset.toInstant()).isBefore(Instant.now().plusSeconds(61));
  }

  @Test
  void learningModeReportsButDoesNotReject() throws Exception {
    config.setLearning(true);
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    Result last = null;
    for (int i = 0; i < 5; i++) {
      last = call(filter, "GET", "/applications");
      assertThat(last.passedThrough()).isTrue();
    }

    assertThat(last.response().getStatus()).isEqualTo(200);
    assertThat(last.response().getHeader("X-RateLimit-Learning")).isEqualTo("true");
    assertThat(last.response().getHeader("X-RateLimit-Remaining")).isEqualTo("0");
  }

  @Test
  void enforcingAndIgnoringPrincipalsOverrideTheGlobalLearningFlag() throws Exception {
    config.setLearning(true);
    config.setEnforcing(List.of("enforced@example.com"));
    var filter = filter();

    authenticateAs(preAuthenticated("enforced@example.com"));
    for (int i = 0; i < 3; i++) {
      call(filter, "GET", "/applications");
    }
    assertThat(call(filter, "GET", "/applications").response().getStatus()).isEqualTo(429);

    authenticateAs(preAuthenticated("learning@example.com"));
    for (int i = 0; i < 4; i++) {
      assertThat(call(filter, "GET", "/applications").response().getStatus()).isEqualTo(200);
    }
  }

  @Test
  void principalsHaveIndependentBuckets() throws Exception {
    var filter = filter();

    authenticateAs(preAuthenticated("alice@example.com"));
    for (int i = 0; i < 4; i++) {
      call(filter, "GET", "/applications");
    }
    assertThat(call(filter, "GET", "/applications").response().getStatus()).isEqualTo(429);

    authenticateAs(preAuthenticated("bob@example.com"));
    assertThat(call(filter, "GET", "/applications").response().getStatus()).isEqualTo(200);
  }

  /** Regression: requests naming deck as their source app used to bypass rate limiting. */
  @Test
  void deckSourceAppIsNotExempt() throws Exception {
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    for (int i = 0; i < 3; i++) {
      assertThat(
              call(filter, "GET", "/applications", r -> r.addHeader("X-RateLimit-App", "deck"))
                  .passedThrough())
          .isTrue();
    }

    Result rejected =
        call(filter, "GET", "/applications", r -> r.addHeader("X-RateLimit-App", "deck"));
    assertThat(rejected.response().getStatus()).isEqualTo(429);
  }

  @Test
  void claimingADifferentSourceAppDoesNotGetAFreshBucket() throws Exception {
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    for (int i = 0; i < 3; i++) {
      call(filter, "GET", "/applications");
    }

    Result rejected =
        call(filter, "GET", "/applications", r -> r.addHeader("X-RateLimit-App", "deck"));
    assertThat(rejected.response().getStatus()).isEqualTo(429);
  }

  private static Authentication anonymous() {
    return new AnonymousAuthenticationToken(
        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
  }

  /** Anonymous callers can't be told apart, so changing IP or any header must not help. */
  @Test
  void allAnonymousCallersShareOneBucket() throws Exception {
    config.setAnonymousCapacity(3);
    authenticateAs(anonymous());
    var filter = filter();

    for (int i = 0; i < 3; i++) {
      String variation = "client-" + i;
      Result result =
          call(
              filter,
              "GET",
              "/applications",
              r -> {
                r.setRemoteAddr("10.0.1." + variation.hashCode() % 200);
                r.addHeader("X-Forwarded-For", "1.1.1." + variation.hashCode() % 200);
                r.addHeader("X-RateLimit-App", variation);
              });
      assertThat(result.passedThrough()).isTrue();
      assertThat(result.response().getHeader("X-RateLimit-Capacity")).isEqualTo("3");
    }

    Result rejected =
        call(
            filter,
            "GET",
            "/applications",
            r -> {
              r.setRemoteAddr("192.168.9.9");
              r.addHeader("X-Forwarded-For", "9.9.9.9");
            });
    assertThat(rejected.response().getStatus()).isEqualTo(429);
    assertThat(requestsCounted("anonymous")).isEqualTo(4);
  }

  @Test
  void anonymousBucketIsLargerThanTheDefaultAndSeparateFromUsers() throws Exception {
    authenticateAs(anonymous());

    Result result = call(filter(), "GET", "/applications");

    assertThat(result.response().getHeader("X-RateLimit-Capacity")).isEqualTo("600");

    authenticateAs(preAuthenticated("alice@example.com"));
    assertThat(call(filter(), "GET", "/applications").response().getHeader("X-RateLimit-Capacity"))
        .isEqualTo("3");
  }

  @Test
  void anonymousKorkUserSharesTheAnonymousBucket() throws Exception {
    authenticateAs(
        new AnonymousAuthenticationToken(
            "key", korkUser("anonymous"), AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    call(filter(), "GET", "/applications");

    assertThat(requestsCounted("anonymous")).isEqualTo(1);
  }

  static Stream<Arguments> authenticationsByMechanism() {
    String name = "alice@example.com";
    return Stream.of(
        // saml (ResponseAuthenticationConverter) and api tokens (ApiTokenAuthenticationFilter)
        Arguments.of(
            "pre-authenticated kork user (saml, api token)",
            (Supplier<Authentication>) () -> preAuthenticated(name)),
        // basic, ldap
        Arguments.of(
            "username/password kork user (basic, ldap)",
            (Supplier<Authentication>)
                () -> new UsernamePasswordAuthenticationToken(korkUser(name), "N/A", List.of())),
        // oauth2 / oidc, via SpinnakerOAuth2UserInfoService
        Arguments.of(
            "oauth2 login with a spinnaker user",
            (Supplier<Authentication>)
                () -> {
                  var user =
                      new SpinnakerOAuth2User(
                          name, "Alice", "Example", List.of(), List.of(), name, Map.of(),
                          List.of());
                  return new OAuth2AuthenticationToken(user, List.of(), "github");
                }),
        // anything else spring security could hand us: fall back to Authentication#getName
        Arguments.of(
            "oauth2 login with a plain oauth2 user",
            (Supplier<Authentication>)
                () -> {
                  var user =
                      new DefaultOAuth2User(
                          AuthorityUtils.createAuthorityList("ROLE_USER"),
                          Map.of("email", name),
                          "email");
                  return new OAuth2AuthenticationToken(user, user.getAuthorities(), "github");
                }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("authenticationsByMechanism")
  void everyAuthenticationMechanismIsLimitedPerUser(
      String mechanism, Supplier<Authentication> authentication) throws Exception {
    authenticateAs(authentication.get());
    var filter = filter();

    for (int i = 0; i < 3; i++) {
      assertThat(call(filter, "GET", "/applications").passedThrough()).isTrue();
    }

    assertThat(requestsCounted("alice@example.com")).isEqualTo(3);
    assertThat(call(filter, "GET", "/applications").response().getStatus()).isEqualTo(429);
  }

  @Test
  void usesTheRequestIdentityExtractorWhenThereIsNoAuthentication() throws Exception {
    extractors.add(
        new RequestIdentityExtractor() {
          @Override
          public boolean supports(HttpServletRequest request) {
            return true;
          }

          @Override
          public String extractIdentity(HttpServletRequest request) {
            return "cert-user@example.com";
          }
        });

    call(filter(), "GET", "/applications");

    assertThat(requestsCounted("cert-user@example.com")).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/health", "/health/liveness", "/actuator/prometheus", "/auth/user"})
  void healthAndActuatorEndpointsAreNeverRateLimited(String uri) throws Exception {
    config.setCapacity(1);
    authenticateAs(
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    var filter = filter();

    for (int i = 0; i < 5; i++) {
      Result result = call(filter, "GET", uri);
      assertThat(result.passedThrough()).isTrue();
      assertThat(result.response().getHeader("X-RateLimit-Capacity")).isNull();
    }

    assertThat(requestsCounted("anonymous")).isZero();
  }

  @Test
  void requestsWithNoIdentityAreNotCounted() throws Exception {
    Result result = call(filter(), "GET", "/applications");

    assertThat(result.passedThrough()).isTrue();
    assertThat(result.response().getHeader("X-RateLimit-Capacity")).isNull();
  }

  @Test
  void ruleBucketsApplyInsteadOfTheDefaultBucket() throws Exception {
    Rule rule = new Rule();
    rule.setName("pipeline-start");
    rule.setMethods(List.of("POST"));
    rule.setPathPatterns(List.of("/pipelines/**"));
    rule.setCapacity(2);
    rule.setRateSeconds(60);
    config.setRules(List.of(rule));
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    Result first = call(filter, "POST", "/pipelines/abc");
    assertThat(first.response().getHeader("X-RateLimit-Rule")).isEqualTo("pipeline-start");
    assertThat(first.response().getHeader("X-RateLimit-Capacity")).isEqualTo("2");
    assertThat(first.response().getHeader("X-RateLimit-Remaining")).isEqualTo("1");

    // a trailing slash must not sidestep the rule
    assertThat(call(filter, "POST", "/pipelines/abc/").passedThrough()).isTrue();
    Result rejected = call(filter, "POST", "/pipelines/abc");
    assertThat(rejected.response().getStatus()).isEqualTo(429);
    assertThat(rejected.response().getHeader("Retry-After")).isNotNull();

    // other requests keep using the default bucket, which the rule traffic did not touch
    Result other = call(filter, "GET", "/pipelines/abc");
    assertThat(other.response().getStatus()).isEqualTo(200);
    assertThat(other.response().getHeader("X-RateLimit-Rule")).isNull();
    assertThat(other.response().getHeader("X-RateLimit-Capacity")).isEqualTo("3");
    assertThat(other.response().getHeader("X-RateLimit-Remaining")).isEqualTo("2");
    assertThat(requestsCounted("alice@example.com")).isEqualTo(1);
  }

  @Test
  void ruleCanBeInLearningModeWhileThePrincipalIsEnforced() throws Exception {
    Rule rule = new Rule();
    rule.setName("search");
    rule.setPathPatterns(List.of("/search"));
    rule.setCapacity(1);
    rule.setRateSeconds(60);
    rule.setLearning(true);
    config.setRules(List.of(rule));
    authenticateAs(preAuthenticated("alice@example.com"));
    var filter = filter();

    for (int i = 0; i < 3; i++) {
      Result result = call(filter, "GET", "/search");
      assertThat(result.passedThrough()).isTrue();
      assertThat(result.response().getHeader("X-RateLimit-Learning")).isEqualTo("true");
    }
  }

  /** Regression: downstream 429s used to get a reset header in 1970 (seconds passed as millis). */
  @Test
  void downstream429sGetAReasonableResetAndRetryAfter() throws Exception {
    authenticateAs(preAuthenticated("alice@example.com"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/applications");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain downstream = (req, res) -> ((HttpServletResponse) res).setStatus(429);

    filter().doFilter(request, response, downstream);

    assertThat(response.getHeader("Retry-After")).isNotNull();
    ZonedDateTime reset =
        ZonedDateTime.parse(
            response.getHeader("X-RateLimit-Reset"), DateTimeFormatter.RFC_1123_DATE_TIME);
    assertThat(reset.toInstant()).isAfter(Instant.now().minusSeconds(1));
  }
}
