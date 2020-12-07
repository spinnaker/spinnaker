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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/** Rate limit requests per the configured {@link RateLimiter} and authenticated principal. */
public class RateLimitingFilter extends HttpFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

  private static final String UNKNOWN_PRINCIPAL = "unknown";

  private final RateLimiter rateLimiter;
  private final Registry registry;
  private final RateLimitPrincipalProvider rateLimitPrincipalProvider;
  private final List<RequestIdentityExtractor> requestIdentityExtractors;

  private final Counter throttlingCounter;
  private final Counter learningThrottlingCounter;
  private final Id controllerInvocationsId;

  public RateLimitingFilter(
      RateLimiter rateLimiter,
      Registry registry,
      RateLimitPrincipalProvider rateLimitPrincipalProvider,
      List<RequestIdentityExtractor> requestIdentityExtractors) {
    this.rateLimiter = rateLimiter;
    this.rateLimitPrincipalProvider = rateLimitPrincipalProvider;
    this.requestIdentityExtractors =
        Objects.requireNonNullElseGet(requestIdentityExtractors, Collections::emptyList);
    this.registry = registry;

    throttlingCounter = registry.counter("rateLimit.throttling");
    learningThrottlingCounter = registry.counter("rateLimit.throttlingLearning");

    this.controllerInvocationsId =
        registry
            .createId("controller.invocations")
            .withTag("controller", "unknown")
            .withTag("method", "unknown")
            .withTag("status", "4xx")
            .withTag("statusCode", "429")
            .withTag("success", "false")
            .withTag("cause", "RateLimitingFilter");
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
                  PercentileTimer.get(registry, controllerInvocationsId)
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
    String sourceApp = request.getHeader("X-RateLimit-App");
    MDC.put("sourceApp", sourceApp);

    if (!rateLimitPrincipalProvider.supports(sourceApp)) {
      // api requests from particular source applications (ie. deck) may not be subject to rate
      // limits
      return true;
    }

    String principalName = getPrincipal(request).toString();
    if (UNKNOWN_PRINCIPAL.equals(principalName)) {
      // Occurs when Spring decides to dispatch to /error after we send the initial 429.
      // Pass through so that the JSON error body gets rendered.
      return true;
    }

    RateLimitPrincipal principal =
        rateLimitPrincipalProvider.getPrincipal(principalName, sourceApp);

    Rate rate = rateLimiter.incrementAndGetRate(principal);

    rate.assignHttpHeaders(response, principal.isLearning());
    recordPrincipalMetrics(principal, rate);

    if (principal.isLearning()) {
      if (rate.isThrottled()) {
        learningThrottlingCounter.increment();
        log.warn(
            "Rate limiting principal (principal: {}, rateSeconds: {}, capacity: {}, learning: true)",
            value("principal", principal.getName()),
            value("rateSeconds", rate.rateSeconds),
            value("rateCapacity", rate.capacity));
      }
      return true;
    }

    if (rate.isThrottled()) {
      throttlingCounter.increment();
      log.warn(
          "Rate limiting principal (principal: {}, rateSeconds: {}, capacity: {}, learning: false)",
          value("principal", principal.getName()),
          value("rateSeconds", rate.rateSeconds),
          value("rateCapacity", rate.capacity));

      // use `setStatus` and `write()` to avoid an unnecessary dispath to '/error' and the
      // subsequent spring security chain filter evaluation.
      response.setStatus(TOO_MANY_REQUESTS.value());
      response.getWriter().write("{\"message\": \"Rate capacity exceeded\"}");
      return false;
    }

    return true;
  }

  private void postHandle(HttpServletRequest request, HttpServletResponse response) {
    // Downstreams can return 429's, which we'll want to intercept to provide a reset header
    if (response.getStatus() == 429 && !response.getHeaderNames().contains(Rate.RESET_HEADER)) {
      response.setIntHeader(Rate.CAPACITY_HEADER, -1);
      response.setIntHeader(Rate.REMAINING_HEADER, 0);
      response.setDateHeader(Rate.RESET_HEADER, ZonedDateTime.now().plusSeconds(5).toEpochSecond());
      response.setHeader(Rate.LEARNING_HEADER, "false");
    }
  }

  private Object getPrincipal(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null) {
      String requestIdentity = null;
      for (int i = 0; i < requestIdentityExtractors.size() && requestIdentity == null; i++) {
        RequestIdentityExtractor extractor = requestIdentityExtractors.get(i);
        if (extractor.supports(request)) {
          try {
            requestIdentity = extractor.extractIdentity(request);
          } catch (Throwable t) {
            log.warn(
                "Failure extracting identity from RequestIdentityExtractor {}",
                extractor.getClass().getSimpleName(),
                t);
          }
        }
      }

      if (requestIdentity != null) {
        return requestIdentity;
      }

      return UNKNOWN_PRINCIPAL;
    }

    if (authentication.getPrincipal() instanceof UserDetails) {
      String principal = ((UserDetails) authentication.getPrincipal()).getUsername();

      if ("anonymous".equals(principal)) {
        String rateLimitApp = request.getHeader("X-RateLimit-App");
        if (rateLimitApp != null && !rateLimitApp.equals("")) {
          log.info(
              "Unknown or anonymous principal, using X-RateLimit-App instead: {}",
              value("rateLimitApp", rateLimitApp));
          return rateLimitApp;
        }
      }
      return principal;
    }

    log.warn("Unknown principal type and no X-RateLimit-App header, assuming anonymous");
    return "anonymous-" + sourceIpAddress(request);
  }

  private String sourceIpAddress(HttpServletRequest request) {
    String ip = request.getHeader("X-FORWARDED-FOR");
    if (ip == null) {
      return request.getRemoteAddr();
    }
    return ip;
  }

  private void recordPrincipalMetrics(RateLimitPrincipal principal, Rate rate) {
    Iterable<Tag> tags = Collections.singletonList(new BasicTag("principal", principal.getName()));
    if (rate.isThrottled()) {
      registry.counter("rateLimit.principal.throttled", tags).increment();
    }
    registry.counter("rateLimit.principal.requests", tags).increment();
  }
}
