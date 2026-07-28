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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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

  /**
   * Snapshot of the principal's roles captured at token-creation time. This is the authoritative
   * role source for token authentication in {@code EXTERNAL} group-membership mode (no {@code
   * UserRolesProvider}), where a principal's roles only ever exist on its live login session and
   * cannot be re-resolved from the principal id alone at token-exchange time. Provider-backed
   * deployments resolve roles live on each request (which keeps revocation/refresh working) and so
   * ignore this snapshot. Absent (null) on legacy records and on service-account tokens.
   */
  private List<String> roles;

  /** ISO-8601 expiry timestamp; absent for non-expiring service-account tokens. */
  private String expiresAt;

  private String lastUsedAt;
  private String createdAt;

  /**
   * ISO-8601 timestamp of the last time Gate verified this principal against the local {@code
   * PermissionService}. Used to throttle permission checks to at most once per {@code
   * rejectCheckIntervalSeconds}. Stored in Redis alongside the other fields so all Gate instances
   * share the same throttle state.
   *
   * <p>{@code @JsonAlias("lastFiatCheckAt")} preserves backward compatibility: tokens persisted to
   * Redis under the old field name still deserialize. New records are written under {@code
   * lastAuthCheckAt}.
   */
  @JsonProperty("lastAuthCheckAt")
  @JsonAlias("lastFiatCheckAt")
  private String lastAuthCheckAt;
}
