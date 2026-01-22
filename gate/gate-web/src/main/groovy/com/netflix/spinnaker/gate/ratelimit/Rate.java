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

import jakarta.servlet.http.HttpServletResponse;

public class Rate {

  static final String CAPACITY_HEADER = "X-RateLimit-Capacity";
  static final String REMAINING_HEADER = "X-RateLimit-Remaining";
  static final String RESET_HEADER = "X-RateLimit-Reset";
  static final String LEARNING_HEADER = "X-RateLimit-Learning";

  Integer capacity;
  Integer rateSeconds;
  Integer remaining;
  Long reset;
  Boolean throttled;

  public boolean isThrottled() {
    return throttled;
  }

  public void assignHttpHeaders(HttpServletResponse response, Boolean learning) {
    response.setIntHeader(CAPACITY_HEADER, capacity);
    response.setIntHeader(REMAINING_HEADER, remaining);
    response.setDateHeader(RESET_HEADER, reset);
    response.setHeader(LEARNING_HEADER, learning.toString());
  }
}
