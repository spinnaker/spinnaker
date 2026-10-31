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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.Rule;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitRuleMatcherTest {

  static Rule rule(String name, List<String> methods, List<String> patterns) {
    Rule rule = new Rule();
    rule.setName(name);
    rule.setMethods(methods);
    rule.setPathPatterns(patterns);
    rule.setCapacity(5);
    rule.setRateSeconds(60);
    return rule;
  }

  static Optional<String> matchedRule(RateLimitRuleMatcher matcher, String method, String uri) {
    return matcher.match(new MockHttpServletRequest(method, uri)).map(Rule::getName);
  }

  @Test
  void noRulesMatchNothing() {
    assertThat(matchedRule(new RateLimitRuleMatcher(List.of()), "GET", "/pipelines")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /pipelines/abc, true",
    "post, /pipelines/abc, true",
    "POST, /pipelines, true",
    "POST, /pipelines/, true",
    "POST, /pipelines///, true",
    "POST, /pipelines;jsessionid=1/abc, true",
    "POST, /pipe%6Cines/abc, true",
    "GET, /pipelines/abc, false",
    "POST, /applications/abc, false",
    "POST, /Pipelines/abc, false",
    "POST, /pipelinesX, false",
  })
  void matchesOnMethodAndNormalizedPath(String method, String uri, boolean expected) {
    var matcher =
        new RateLimitRuleMatcher(List.of(rule("start", List.of("POST"), List.of("/pipelines/**"))));

    assertThat(matchedRule(matcher, method, uri).isPresent()).isEqualTo(expected);
  }

  @Test
  void exactPatternsIgnoreTrailingSlashes() {
    var matcher =
        new RateLimitRuleMatcher(List.of(rule("exact", List.of(), List.of("/credentials/"))));

    assertThat(matchedRule(matcher, "GET", "/credentials")).contains("exact");
    assertThat(matchedRule(matcher, "GET", "/credentials/")).contains("exact");
    assertThat(matchedRule(matcher, "GET", "/credentials/foo")).isEmpty();
  }

  @Test
  void emptyMethodsMatchEveryMethod() {
    var matcher = new RateLimitRuleMatcher(List.of(rule("any", List.of(), List.of("/search"))));

    assertThat(matchedRule(matcher, "GET", "/search")).contains("any");
    assertThat(matchedRule(matcher, "DELETE", "/search")).contains("any");
  }

  @Test
  void templateVariablesAndWildcardsWork() {
    var matcher =
        new RateLimitRuleMatcher(
            List.of(rule("app-pipelines", List.of(), List.of("/applications/*/pipelines/**"))));

    assertThat(matchedRule(matcher, "POST", "/applications/orders/pipelines/start"))
        .contains("app-pipelines");
    assertThat(matchedRule(matcher, "POST", "/applications/orders/tasks")).isEmpty();
  }

  @Test
  void firstMatchingRuleWins() {
    var matcher =
        new RateLimitRuleMatcher(
            List.of(
                rule("specific", List.of("POST"), List.of("/pipelines/start")),
                rule("general", List.of(), List.of("/pipelines/**"))));

    assertThat(matchedRule(matcher, "POST", "/pipelines/start")).contains("specific");
    assertThat(matchedRule(matcher, "GET", "/pipelines/start")).contains("general");
  }

  @Test
  void rejectsInvalidRules() {
    Rule noName = rule(null, List.of(), List.of("/a"));
    Rule colonName = rule("a:b", List.of(), List.of("/a"));
    Rule noPatterns = rule("c", List.of(), List.of());
    Rule noCapacity = rule("d", List.of(), List.of("/a"));
    noCapacity.setCapacity(0);
    Rule noWindow = rule("e", List.of(), List.of("/a"));
    noWindow.setRateSeconds(0);
    Rule badPattern = rule("f", List.of(), List.of("/a/**/b"));

    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(noName)))
        .hasMessageContaining("requires a name");
    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(colonName)))
        .hasMessageContaining("may not contain ':'");
    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(noPatterns)))
        .hasMessageContaining("rule 'c' requires at least one path-pattern");
    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(noCapacity)))
        .hasMessageContaining("rule 'd' requires a positive capacity");
    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(noWindow)))
        .hasMessageContaining("rule 'e' requires a positive capacity");
    assertThatThrownBy(() -> new RateLimitRuleMatcher(List.of(badPattern)))
        .hasMessageContaining("rule 'f' has an invalid path-pattern '/a/**/b'");
  }

  @Test
  void rejectsDuplicateNames() {
    assertThatThrownBy(
            () ->
                new RateLimitRuleMatcher(
                    List.of(
                        rule("dup", List.of(), List.of("/a")),
                        rule("dup", List.of(), List.of("/b")))))
        .hasMessageContaining("duplicate rule name 'dup'");
  }

  @ParameterizedTest
  @CsvSource({
    "/health, true",
    "/health/, true",
    "/health/liveness, true",
    "/actuator/prometheus, true",
    "/actuator, true",
    "/auth/user, true",
    "/authors, false",
    "/applications, false",
    "/healthcheck, false",
  })
  void excludedPathsAreNeverRateLimited(String uri, boolean expected) {
    var matcher =
        new RateLimitRuleMatcher(List.of(), List.of("/health/**", "/actuator/**", "/auth/**"));

    assertThat(matcher.isExcluded(new MockHttpServletRequest("GET", uri))).isEqualTo(expected);
  }
}
