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

package com.netflix.spinnaker.gate.controllers;

/**
 * Request body for {@code POST /auth/apiTokens}. Replaces the loose {@code Map<String,Object>} the
 * controller used to accept; all fields are nullable at the wire level and validated server-side.
 *
 * <ul>
 *   <li>{@code name} — required, non-blank.
 *   <li>{@code principalType} — optional; defaults to {@code USER}. Only admins may supply {@code
 *       SERVICE_ACCOUNT}.
 *   <li>{@code principalId} — required for {@code SERVICE_ACCOUNT}; ignored for {@code USER} tokens
 *       (forced to the caller's username).
 *   <li>{@code expiresAt} — optional ISO-8601 instant; capped by {@code
 *       api-tokens.max-{user,service-account}-token-lifetime-days}.
 * </ul>
 */
public record CreateApiTokenRequest(
    String name, String principalType, String principalId, String expiresAt) {}
