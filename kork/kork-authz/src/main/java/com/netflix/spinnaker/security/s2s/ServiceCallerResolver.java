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

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Strategy for reading the authenticated identity of an internal service caller from an inbound
 * request and normalizing it to a {@link ServiceCaller}.
 *
 * <p>Implementations correspond to the transport an install uses to authenticate peers (CA/mTLS
 * subject, service-mesh header, or Kubernetes ServiceAccount token). The selected provider is the
 * only install-specific knob; the resulting {@link SpinnakerService} is the same regardless of
 * mechanism, so the codified authorization policy ({@link AllowServiceCallers}) is
 * transport-independent.
 *
 * <p>A resolver returns {@link Optional#empty()} when no service caller identity is present (e.g. a
 * request from an end user rather than another service). It should never throw for an
 * absent/malformed identity — an absent caller simply fails any {@code @AllowServiceCallers} check.
 */
@FunctionalInterface
public interface ServiceCallerResolver {

  /**
   * Resolves the authenticated service caller for the given request, or {@link Optional#empty()} if
   * the request carries no recognizable service identity.
   */
  Optional<ServiceCaller> resolve(HttpServletRequest request);

  /** A resolver that never identifies a caller; the default when service-to-service auth is off. */
  static ServiceCallerResolver disabled() {
    return request -> Optional.empty();
  }
}
