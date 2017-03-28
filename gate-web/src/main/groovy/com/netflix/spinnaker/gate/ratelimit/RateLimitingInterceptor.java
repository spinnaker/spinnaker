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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import com.netflix.spinnaker.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.ZonedDateTime;

public class RateLimitingInterceptor extends HandlerInterceptorAdapter {

  private static Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

  private static String UNKNOWN_PRINCIPAL = "unknown";

  RateLimiter rateLimiter;
  RateLimiterConfiguration rateLimiterConfiguration;

  private Counter throttlingCounter;

  public RateLimitingInterceptor(RateLimiter rateLimiter, Registry registry, RateLimiterConfiguration rateLimiterConfiguration) {
    this.rateLimiter = rateLimiter;
    this.rateLimiterConfiguration = rateLimiterConfiguration;
    throttlingCounter = registry.counter("rateLimit.throttling");
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String principal = getPrincipal(request).toString();
    if (UNKNOWN_PRINCIPAL.equals(principal)) {
      // Occurs when Spring decides to dispatch to /error after we send the initial 429.
      // Pass through so that the JSON error body gets rendered.
      return true;
    }

    Rate rate = rateLimiter.incrementAndGetRate(principal);

    boolean learning = isLearning(principal);

    rate.assignHttpHeaders(response, learning);

    if (learning) {
      if (rate.isThrottled()) {
        throttlingCounter.increment();
        log.warn("Rate limiting principal (principal: {}, rateSeconds: {}, capacity: {}, learning: true)", principal, rate.rateSeconds, rate.capacity);
      }
      return true;
    }


    if (rate.isThrottled()) {
      throttlingCounter.increment();
      log.warn("Rate limiting principal (principal: {}, rateSeconds: {}, capacity: {}, learning: false)", principal, rate.rateSeconds, rate.capacity);
      response.sendError(429, "Rate capacity exceeded");
      return false;
    }

    return true;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    // Hystrix et-al can return 429's, which we'll want to intercept to provide a reset header
    if (response.getStatus() == 429 && !response.getHeaderNames().contains(Rate.RESET_HEADER)) {
      response.setIntHeader(Rate.CAPACITY_HEADER, -1);
      response.setIntHeader(Rate.REMAINING_HEADER, 0);
      response.setDateHeader(Rate.RESET_HEADER, ZonedDateTime.now().plusSeconds(5).toEpochSecond());
      response.setHeader(Rate.LEARNING_HEADER, "false");
    }
  }

  private Object getPrincipal(HttpServletRequest request) {
    SecurityContext context = SecurityContextHolder.getContext();
    Authentication authentication = context.getAuthentication();

    if (authentication == null) {
      return UNKNOWN_PRINCIPAL;
    }

    if (authentication.getPrincipal() instanceof User) {
      return ((User) authentication.getPrincipal()).getEmail();
    }

    log.warn("Unknown principal type, assuming anonymous");
    return "anonymous-" + sourceIpAddress(request);
  }

  private String sourceIpAddress(HttpServletRequest request) {
    String ip = request.getHeader("X-FORWARDED-FOR");
    if (ip == null) {
      return request.getRemoteAddr();
    }
    return ip;
  }

  private boolean isLearning(String principal) {
    return !rateLimiterConfiguration.getEnforcing().contains(principal) && (rateLimiterConfiguration.getIgnoring().contains(principal) || rateLimiterConfiguration.isLearning());
  }
}
