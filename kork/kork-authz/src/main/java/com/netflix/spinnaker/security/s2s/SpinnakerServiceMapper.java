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

/**
 * Maps a raw service <em>name</em> extracted from a transport identity (e.g. an X.509 CN, a mesh
 * SPIFFE workload segment, or a Kubernetes ServiceAccount name) to a {@link SpinnakerService}.
 *
 * <p>This is <em>recognition</em>, not authorization: it only decides which service a peer is, by
 * convention (an optional deployment prefix such as {@code spin-} stripped, then matched
 * case-insensitively against the {@link SpinnakerService} enum). It never decides what a service is
 * permitted to do — that is codified in {@link AllowServiceCallers}.
 */
public final class SpinnakerServiceMapper {

  private final String namePrefix;

  /**
   * @param namePrefix an optional deployment prefix stripped before matching (e.g. {@code "spin-"}
   *     so a ServiceAccount {@code spin-orca} maps to {@link SpinnakerService#ORCA}); may be null
   *     or empty to disable prefix stripping
   */
  public SpinnakerServiceMapper(String namePrefix) {
    this.namePrefix = namePrefix == null ? "" : namePrefix;
  }

  /** Maps a raw service name to a {@link SpinnakerService}, or {@link SpinnakerService#UNKNOWN}. */
  public SpinnakerService map(String rawName) {
    if (rawName == null) {
      return SpinnakerService.UNKNOWN;
    }
    String name = rawName.trim();
    if (!namePrefix.isEmpty() && name.regionMatches(true, 0, namePrefix, 0, namePrefix.length())) {
      name = name.substring(namePrefix.length());
    }
    return SpinnakerService.fromName(name);
  }
}
