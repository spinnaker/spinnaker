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

/**
 * Thrown when a Spinnaker identity token fails verification (bad signature, expired, wrong issuer
 * or audience, or malformed).
 *
 * <p>With authorization disabled (see {@code authz.enabled=false}) callers are expected to catch
 * this and fall back to the unsigned identity headers; when enabled the request is rejected.
 */
public class TokenValidationException extends RuntimeException {
  public TokenValidationException(String message) {
    super(message);
  }

  public TokenValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
