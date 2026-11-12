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

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.Rule;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Finds the first configured {@link Rule} that applies to a request.
 *
 * <p>Rules are validated when the matcher is built so that a typo in configuration prevents startup
 * rather than silently leaving an endpoint unprotected.
 */
public class RateLimitRuleMatcher {

  private final List<CompiledRule> rules;
  private final List<PathPattern> excluded;

  public RateLimitRuleMatcher(List<Rule> configuredRules) {
    this(configuredRules, List.of());
  }

  public RateLimitRuleMatcher(List<Rule> configuredRules, List<String> excludePathPatterns) {
    this.excluded =
        excludePathPatterns.stream()
            .map(pattern -> parsePattern("rate-limit.exclude-path-patterns", pattern))
            .toList();
    Set<String> names = new HashSet<>();
    this.rules =
        configuredRules.stream()
            .map(
                rule -> {
                  validate(rule, names);
                  return compile(rule);
                })
            .toList();
  }

  /** Whether the request is exempt from rate limiting entirely, e.g. health checks. */
  public boolean isExcluded(HttpServletRequest request) {
    if (excluded.isEmpty()) {
      return false;
    }

    PathContainer path = pathOf(request);
    return excluded.stream().anyMatch(pattern -> pattern.matches(path));
  }

  public Optional<Rule> match(HttpServletRequest request) {
    if (rules.isEmpty()) {
      return Optional.empty();
    }

    PathContainer path = pathOf(request);
    String method = request.getMethod().toUpperCase(Locale.ROOT);

    return rules.stream()
        .filter(rule -> rule.matches(method, path))
        .map(rule -> rule.rule)
        .findFirst();
  }

  private static PathContainer pathOf(HttpServletRequest request) {
    RequestPath requestPath = ServletRequestPathUtils.parseAndCache(request);
    return PathContainer.parsePath(normalize(requestPath.pathWithinApplication().value()));
  }

  private static void validate(Rule rule, Set<String> names) {
    String name = rule.getName();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("rate-limit.rules: every rule requires a name");
    }
    if (name.contains(":")) {
      throw new IllegalArgumentException(
          "rate-limit.rules: rule name '" + name + "' may not contain ':'");
    }
    if (!names.add(name)) {
      throw new IllegalArgumentException("rate-limit.rules: duplicate rule name '" + name + "'");
    }
    if (rule.getPathPatterns() == null || rule.getPathPatterns().isEmpty()) {
      throw new IllegalArgumentException(
          "rate-limit.rules: rule '" + name + "' requires at least one path-pattern");
    }
    if (rule.getCapacity() <= 0 || rule.getRateSeconds() <= 0) {
      throw new IllegalArgumentException(
          "rate-limit.rules: rule '" + name + "' requires a positive capacity and rate-seconds");
    }
  }

  private static CompiledRule compile(Rule rule) {
    List<PathPattern> patterns =
        rule.getPathPatterns().stream()
            .map(
                pattern -> parsePattern("rate-limit.rules: rule '" + rule.getName() + "'", pattern))
            .toList();

    Set<String> methods = new HashSet<>();
    if (rule.getMethods() != null) {
      rule.getMethods().forEach(method -> methods.add(method.toUpperCase(Locale.ROOT)));
    }

    return new CompiledRule(rule, methods, patterns);
  }

  private static PathPattern parsePattern(String owner, String pattern) {
    try {
      return PathPatternParser.defaultInstance.parse(normalize(pattern));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          owner + " has an invalid path-pattern '" + pattern + "': " + e.getMessage(), e);
    }
  }

  /**
   * Gate runs with {@code ANT_PATH_MATCHER}, which routes {@code /foo/} to the same handler as
   * {@code /foo}, so both have to be treated identically or a trailing slash would sidestep a rule.
   */
  private static String normalize(String path) {
    int end = path.length();
    while (end > 1 && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(0, end).isEmpty() ? "/" : path.substring(0, end);
  }

  private record CompiledRule(Rule rule, Set<String> methods, List<PathPattern> patterns) {
    boolean matches(String method, PathContainer path) {
      return (methods.isEmpty() || methods.contains(method))
          && patterns.stream().anyMatch(p -> p.matches(path));
    }
  }
}
