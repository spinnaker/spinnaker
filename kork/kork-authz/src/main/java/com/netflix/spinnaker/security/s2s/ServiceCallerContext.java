/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s;

import java.util.Optional;

/**
 * Holds the {@link ServiceCaller} authenticated for the current request thread.
 *
 * <p>Populated by {@link
 * com.netflix.spinnaker.security.s2s.filter.ServiceCallerAuthenticationFilter} at the start of
 * request processing and cleared when the request completes. Authorization ({@link
 * com.netflix.spinnaker.security.s2s.ServiceCallerEnforcementAspect}) reads it here so it never has
 * to re-parse the transport identity. It is deliberately <em>not</em> propagated into the {@code
 * X-SPINNAKER-*} MDC: each hop authenticates its own immediate peer, so a caller identity must not
 * leak onto this service's own outbound requests.
 */
public final class ServiceCallerContext {

  /** Request attribute key under which the current {@link ServiceCaller} is also exposed. */
  public static final String REQUEST_ATTRIBUTE = ServiceCaller.class.getName();

  private static final ThreadLocal<ServiceCaller> CURRENT = new ThreadLocal<>();

  private ServiceCallerContext() {}

  /** Sets the caller for the current thread. Intended for the authentication filter. */
  public static void set(ServiceCaller caller) {
    CURRENT.set(caller);
  }

  /** Clears the caller for the current thread. Must be called in a {@code finally} block. */
  public static void clear() {
    CURRENT.remove();
  }

  /** The service caller authenticated for the current request, if any. */
  public static Optional<ServiceCaller> current() {
    return Optional.ofNullable(CURRENT.get());
  }
}
