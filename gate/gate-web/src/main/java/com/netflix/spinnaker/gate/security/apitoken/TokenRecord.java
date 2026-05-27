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

package com.netflix.spinnaker.gate.security.apitoken;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gate-side model for an API token stored in Redis. All fields are round-tripped as JSON values of
 * the Redis key.
 *
 * <p>Key schema:
 *
 * <pre>
 *   {prefix}:hash:{sha256_hex}   → JSON (this object)
 *   {prefix}:id:{uuid}           → JSON (same object)
 *   {prefix}:principal:{TYPE}:{id} → SET of token UUIDs
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenRecord {

  private String id;
  private String name;

  /**
   * SHA-256 hex of the raw token value. Persisted in the id-key JSON so that revocation by ID can
   * find and delete the corresponding hash key in O(1). Not exposed in API responses (stripped by
   * the controller's {@code toPublicMap}).
   */
  private String hashRef;

  private String principalId;
  private String principalType;
  private String createdByUserId;

  /** ISO-8601 expiry timestamp; absent for non-expiring service-account tokens. */
  private String expiresAt;

  private String lastUsedAt;
  private String createdAt;

  /**
   * ISO-8601 timestamp of the last time Gate verified this principal against Fiat. Used to throttle
   * Fiat checks to at most once per {@code rejectCheckIntervalSeconds}. Stored in Redis alongside
   * the other fields so all Gate instances share the same throttle state.
   */
  private String lastFiatCheckAt;
}
