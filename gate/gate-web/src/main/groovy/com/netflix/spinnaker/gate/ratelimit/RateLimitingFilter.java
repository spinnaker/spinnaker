/*
 * Copyright 2017 Netflix, Inc.
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

import static com.netflix.spinnaker.gate.filters.RequestLoggingFilter.REQUEST_START_TIME;
import static net.logstash.logback.argument.StructuredArguments.value;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.Rule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/** Rate limit requests per the configured {@link RateLimiter} and authenticated principal. */
@Slf4j
public class RateLimitingFilter extends HttpFilter {

  private final RateLimiter rateLimiter;
  private final MeterRegistry registry;
  private final RateLimitPrincipalProvider rateLimitPrincipalProvider;
  private final RateLimitPrincipalResolver principalResolver;
  private final RateLimitRuleMatcher ruleMatcher;

  private final Counter throttlingCounter;
  private final Counter learningThrottlingCounter;
  private final Timer.Builder controllerInvocations;

  public RateLimitingFilter(
      RateLimiter rateLimiter,
      MeterRegistry registry,
      RateLimitPrincipalProvider rateLimitPrincipalProvider,
      RateLimitPrincipalResolver principalResolver,
      RateLimitRuleMatcher ruleMatcher) {
    this.rateLimiter = rateLimiter;
    this.ruleMatcher = ruleMatcher;
    this.rateLimitPrincipalProvider = rateLimitPrincipalProvider;
    this.principalResolver = principalResolver;
    this.registry = registry;

    throttlingCounter = registry.counter("rateLimit.throttling");
    learningThrottlingCounter = registry.counter("rateLimit.throttlingLearning");

    this.controllerInvocations =
        Timer.builder("controller.invocations")
            .tags(
                "controller", "unknown",
                "method", "unknown",
                "status", "4xx",
                "statusCode", "429",
                "success", "false",
                "cause", "RateLimitingFilter");
  }

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      if (!preHandle(request, response)) {
        Optional.ofNullable(MDC.get(REQUEST_START_TIME))
            .ifPresent(
                startTime -> {
                  controllerInvocations
                      .register(registry)
                      .record(
                          System.currentTimeMillis() - Long.parseLong(startTime),
                          TimeUnit.MILLISECONDS);
                });
        return;
      }

      chain.doFilter(request, response);
    } finally {
      postHandle(request, response);
    }
  }

  private boolean preHandle(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (ruleMatcher.isExcluded(request)) {
      return true;
    }

    // The source app is client-supplied and therefore untrusted: it may influence which configured
    // capacity applies, but it never exempts a request from rate limiting.
    String sourceApp = request.getHeader("X-RateLimit-App");
    MDC.put("sourceApp", sourceApp);

    Optional<String> resolved = principalResolver.resolve(request);
    if (resolved.isEmpty()) {
      // Occurs when Spring decides to dispatch to /error after we send the initial 429.
      // Pass through so that the JSON error body gets rendered.
      return true;
    }
    String principalName = resolved.get();

    RateLimitPrincipal principal =
        rateLimitPrincipalProvider.getPrincipal(
            principalName, sourceApp, () -> principalResolver.resolveRoles(request));

    Optional<Rule> rule = ruleMatcher.match(request);
    boolean learning = rule.map(Rule::getLearning).orElse(principal.isLearning());

    Rate rate;
    if (rule.isPresent()) {
      rate =
          rateLimiter.incrementAndGetRate(
              rule.get().getName(),
              new RateLimitPrincipal(
                  principalName, rule.get().getRateSeconds(), rule.get().getCapacity(), learning));
      response.setHeader(Rate.RULE_HEADER, rule.get().getName());
    } else {
      rate = rateLimiter.incrementAndGetRate(principal);
    }

    rate.assignHttpHeaders(response, learning);
    recordPrincipalMetrics(principal, rate);
    rule.ifPresent(r -> recordRuleMetrics(r, rate));

    String ruleName = rule.map(Rule::getName).orElse(null);
    if (rate.isThrottled()) {
      if (learning) {
        learningThrottlingCounter.increment();
      } else {
        throttlingCounter.increment();
      }

      log.warn(
          "Rate limiting principal (principal: {}, rule: {}, rateSeconds: {}, capacity: {}, learning: {})",
          value("principal", principal.getName()),
          value("rule", ruleName),
          value("rateSeconds", rate.rateSeconds),
          value("rateCapacity", rate.capacity),
          value("learning", learning));

      if (!learning) {
        rejectRequest(response, rate);
        return false;
      }
    }

    return true;
  }

  /**
   * Use `setStatus` and `write()` to avoid an unnecessary dispatch to '/error' and the subsequent
   * spring security filter chain evaluation.
   */
  private void rejectRequest(HttpServletResponse response, Rate rate) throws IOException {
    int retryAfterSeconds = retryAfterSeconds(rate.untilReset());

    response.setStatus(TOO_MANY_REQUESTS.value());
    response.setHeader(Rate.RETRY_AFTER_HEADER, Integer.toString(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"message\": \"Rate capacity exceeded\", \"retryAfterSeconds\": "
                + retryAfterSeconds
                + "}");
  }

  private void postHandle(HttpServletRequest request, HttpServletResponse response) {
    // Downstreams can return 429's, which we'll want to intercept to provide reset/retry headers
    if (response.getStatus() != 429) {
      return;
    }

    if (!response.containsHeader(Rate.RESET_HEADER)) {
      response.setIntHeader(Rate.CAPACITY_HEADER, -1);
      response.setIntHeader(Rate.REMAINING_HEADER, 0);
      response.setDateHeader(Rate.RESET_HEADER, System.currentTimeMillis() + 5000);
      response.setHeader(Rate.LEARNING_HEADER, "false");
    }

    if (!response.containsHeader(Rate.RETRY_AFTER_HEADER)) {
      // counted requests already carry a reset header from preHandle; derive the delay from it
      Instant reset =
          ZonedDateTime.parse(
                  response.getHeader(Rate.RESET_HEADER), DateTimeFormatter.RFC_1123_DATE_TIME)
              .toInstant();
      response.setIntHeader(
          Rate.RETRY_AFTER_HEADER, retryAfterSeconds(Duration.between(Instant.now(), reset)));
    }
  }

  /** Retry-After is whole seconds, rounded up, and never tells a caller to retry immediately. */
  private static int retryAfterSeconds(Duration untilReset) {
    return (int) Math.max(1, untilReset.plusMillis(999).toSeconds());
  }

  private void recordRuleMetrics(Rule rule, Rate rate) {
    if (rate.isThrottled()) {
      registry.counter("rateLimit.rule.throttled", "rule", rule.getName()).increment();
    }
    registry.counter("rateLimit.rule.requests", "rule", rule.getName()).increment();
  }

  private void recordPrincipalMetrics(RateLimitPrincipal principal, Rate rate) {
    if (rate.isThrottled()) {
      registry
          .counter("rateLimit.principal.throttled", "principal", principal.getName())
          .increment();
    }
    registry.counter("rateLimit.principal.requests", "principal", principal.getName()).increment();
  }
}
