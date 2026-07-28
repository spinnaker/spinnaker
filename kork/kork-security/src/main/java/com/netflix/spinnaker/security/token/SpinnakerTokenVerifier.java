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

package com.netflix.spinnaker.security.token;

import javax.annotation.Nonnull;

/**
 * Verifies signed Spinnaker identity tokens.
 *
 * <p>Verification checks the JWT signature against the configured public key(s) (with JWKS-based
 * rotation support) and validates the registered claims ({@code exp}/{@code iss}/{@code aud}) with
 * a configurable clock-skew tolerance.
 */
public interface SpinnakerTokenVerifier {

  /**
   * Verify the supplied compact-serialized token and return its claims.
   *
   * @param serializedToken the compact-serialized signed JWT
   * @return the verified claims
   * @throws TokenValidationException if the signature or any registered claim is invalid
   */
  @Nonnull
  SpinnakerTokenClaims verify(@Nonnull String serializedToken) throws TokenValidationException;
}
