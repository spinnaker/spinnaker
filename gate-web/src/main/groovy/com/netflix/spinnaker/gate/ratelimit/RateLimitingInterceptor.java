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
import com.netflix.spinnaker.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RateLimitingInterceptor extends HandlerInterceptorAdapter {

  private static Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

  private static String UNKNOWN_PRINCIPAL = "unknown";

  RateLimiter rateLimiter;
  boolean learning;

  private Counter throttlingCounter;

  public RateLimitingInterceptor(RateLimiter rateLimiter, Registry registry, boolean learning) {
    this.rateLimiter = rateLimiter;
    this.learning = learning;
    throttlingCounter = registry.counter("rateLimit.throttling");
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String principal = getPrincipal().toString();
    if (UNKNOWN_PRINCIPAL.equals(principal)) {
      // Occurs when Spring decides to dispatch to /error after we send the initial 429.
      // Pass through so that the JSON error body gets rendered.
      return true;
    }

    Rate rate = rateLimiter.incrementAndGetRate(principal);

    if (learning) {
      if (rate.isThrottled()) {
        throttlingCounter.increment();
        log.warn("Rate limiting principal (principal: {}, learning: true)", principal);
      }
      return true;
    }

    rate.assignHttpHeaders(response);

    if (rate.isThrottled()) {
      throttlingCounter.increment();
      log.warn("Rate limiting principal (principal: {}, rateSeconds: {}, capacity: {})", principal, rate.rateSeconds, rate.capacity);
      response.sendError(429, "Rate capacity exceeded");
      return false;
    }

    return true;
  }

  private Object getPrincipal() {
    SecurityContext context = SecurityContextHolder.getContext();
    Authentication authentication = context.getAuthentication();

    if (authentication == null) {
      return UNKNOWN_PRINCIPAL;
    }

    if (authentication.getPrincipal() instanceof User) {
      return ((User) authentication.getPrincipal()).getEmail();
    }

    log.warn("Unknown principal type, assuming anonymous");
    return "anonymous";
  }
}
