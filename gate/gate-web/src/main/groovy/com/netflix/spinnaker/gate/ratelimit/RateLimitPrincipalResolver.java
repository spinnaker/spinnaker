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

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Decides whose rate limit bucket a request counts against. Define a bean of this type to replace
 * the default, e.g. to limit per tenant or per API client rather than per user.
 */
public interface RateLimitPrincipalResolver {

  /**
   * @return the principal name to limit the request as, or empty to not rate limit the request
   */
  Optional<String> resolve(HttpServletRequest request);
}
