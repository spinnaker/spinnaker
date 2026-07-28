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

import java.util.Locale;
import java.util.Optional;

/**
 * The closed set of first-party Spinnaker microservices that can be an authenticated
 * service-to-service caller.
 *
 * <p>This enum is deliberately compiled in rather than configured: which service may invoke which
 * internal endpoint is an invariant of Spinnaker's architecture (expressed via {@link
 * AllowServiceCallers}), reviewed in a pull request, and must not be something an operator can
 * widen from config. Install-specific configuration is limited to <em>how</em> a caller's transport
 * identity is read (mTLS subject, mesh header, or Kubernetes ServiceAccount token) — never
 * <em>what</em> a caller is allowed to do.
 *
 * <p>{@link #UNKNOWN} represents an authenticated peer whose identity did not resolve to a known
 * Spinnaker service; it is never granted any {@link AllowServiceCallers} permission.
 */
public enum SpinnakerService {
  GATE,
  ORCA,
  ECHO,
  CLOUDDRIVER,
  FRONT50,
  IGOR,
  ROSCO,
  KAYENTA,
  KEEL,
  UNKNOWN;

  /**
   * Resolves a logical service name (e.g. {@code "orca"}, case-insensitive) to a {@link
   * SpinnakerService}, returning {@link #UNKNOWN} for any name that is not a recognized first-party
   * service rather than throwing.
   */
  public static SpinnakerService fromName(String name) {
    if (name == null || name.isBlank()) {
      return UNKNOWN;
    }
    try {
      return valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }

  /** Whether this resolved to a recognized first-party Spinnaker service. */
  public boolean isKnown() {
    return this != UNKNOWN;
  }

  /** Convenience for callers that prefer an {@link Optional} over an {@link #UNKNOWN} sentinel. */
  public Optional<SpinnakerService> known() {
    return isKnown() ? Optional.of(this) : Optional.empty();
  }
}
