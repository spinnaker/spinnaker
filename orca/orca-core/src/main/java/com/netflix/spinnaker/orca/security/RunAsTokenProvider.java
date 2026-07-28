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

package com.netflix.spinnaker.orca.security;

import java.util.Collection;
import java.util.Optional;

/**
 * Re-issues the short-lived signed identity token an execution propagates downstream, from the
 * authorization grant captured when the execution was admitted.
 *
 * <p>An execution's authorization is decided once, at admission. The launching subject and the
 * roles they were authorized with are captured then (the original token is short-lived and is not
 * persisted). At each asynchronous stage boundary — where the original token has expired and no
 * request header survives — Orca asks the token authority (Front50) to issue a fresh, short-lived
 * identity token carrying exactly that captured subject + roles, and propagates it on {@code
 * Header.IDENTITY_TOKEN}. Roles are never re-resolved (the only option under EXTERNAL group
 * membership) — they are the admission-time grant, replayed.
 *
 * <p>Orca holds <b>no</b> signing key and is <em>not</em> a token minter. Authorization is the
 * service-to-service caller identity: Front50 requires the authenticated caller to be Orca (via
 * {@code authz.s2s}: mTLS / mesh / Kubernetes ServiceAccount token), so the already-admitted
 * subject + roles are relayed in the request body and trusted because the channel itself is
 * authenticated. This preserves the invariant that only Gate and Front50 hold an identity-token
 * minting key.
 */
public interface RunAsTokenProvider {

  /**
   * Issue a fresh, short-lived identity token for an in-flight execution's already-admitted
   * subject.
   *
   * @param subject the principal the execution was admitted to run as (a user, or a service
   *     account)
   * @param roles the roles captured at admission; replayed verbatim into the issued token. May be
   *     empty for a known service account, in which case the authority re-resolves its {@code
   *     memberOf}.
   * @param admin whether the subject was admitted with Spinnaker admin
   * @param accountManager whether the subject was admitted with account-manager status
   * @return the compact-serialized fresh token, or empty when one cannot be obtained (so the caller
   *     proceeds without failing an already-admitted execution)
   */
  Optional<String> issueExecutionToken(
      String subject, Collection<String> roles, boolean admin, boolean accountManager);
}
