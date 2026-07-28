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

package com.netflix.spinnaker.gate.filters;

/**
 * Shared {@code ServletRequest} attribute keys used to communicate authentication classifications
 * across the request lifecycle.
 *
 * <p>Lives in {@code gate-core} so filters in either module (e.g. {@link FiatSessionFilter} here in
 * gate-core, {@code ApiTokenAuthenticationFilter} in gate-web) can reference the same symbol
 * without inverting the gate-core ← gate-web module dependency.
 */
public final class AuthRequestAttributes {

  /**
   * Set to {@code Boolean.TRUE} by the API-token auth filter once it decides the inbound request is
   * bearing a Spinnaker API token. Consumed by downstream filters that should skip session / DPoP /
   * browser-flow logic for stateless token requests (e.g. {@link FiatSessionFilter}).
   */
  public static final String IS_API_TOKEN = "gate.isApiToken";

  private AuthRequestAttributes() {}
}
