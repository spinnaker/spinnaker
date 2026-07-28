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

package com.netflix.spinnaker.orca.front50;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.front50.model.ExecutionTokenRequest;
import com.netflix.spinnaker.orca.front50.model.RunAsTokenResponse;
import com.netflix.spinnaker.orca.security.RunAsTokenProvider;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collection;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link RunAsTokenProvider} backed by Front50's execution-token endpoint ({@code POST
 * /auth/issueExecutionToken}). This keeps Front50 the sole run-as token authority (besides Gate):
 * Orca relays an in-flight execution's already-admitted subject + roles and receives a freshly
 * minted, short-lived identity token carrying exactly those claims.
 *
 * <p>Orca holds <b>no</b> signing key. Authorization is the service-to-service caller identity —
 * Front50 requires the authenticated caller to be Orca (via {@code authz.s2s}: mTLS / mesh /
 * Kubernetes ServiceAccount token) — so the already-admitted subject/roles can be sent in the
 * request body and trusted because the channel itself is authenticated. This satisfies the
 * invariant that only Gate and Front50 hold an identity-token minting key.
 *
 * <p>Failures are swallowed (empty result) so a transient Front50 problem degrades to propagating
 * the unsigned {@code X-SPINNAKER-USER} header rather than failing an already-admitted execution.
 *
 * <p>Disabled via {@code authz.runas.remint.enabled=false}.
 */
@Component
@ConditionalOnProperty(value = "authz.runas.remint.enabled", matchIfMissing = true)
public class Front50RunAsTokenProvider implements RunAsTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(Front50RunAsTokenProvider.class);

  private final Front50Service front50Service;
  private final boolean enabled;

  public Front50RunAsTokenProvider(
      Front50Service front50Service, @Value("${authz.runas.remint.enabled:true}") boolean enabled) {
    this.front50Service = front50Service;
    this.enabled = enabled;
  }

  @Override
  public Optional<String> issueExecutionToken(
      String subject, Collection<String> roles, boolean admin, boolean accountManager) {
    if (!enabled || front50Service == null || StringUtils.isBlank(subject)) {
      return Optional.empty();
    }
    try {
      ExecutionTokenRequest request =
          new ExecutionTokenRequest(subject, roles, admin, accountManager);
      // Mint without inheriting any ambient identity: the authenticated s2s caller identity is the
      // proof, and the already-admitted subject/roles ride in the body.
      RunAsTokenResponse response =
          AuthenticatedRequest.allowAnonymous(
              () -> Retrofit2SyncCall.execute(front50Service.issueExecutionToken(request)));
      if (response == null || StringUtils.isBlank(response.getToken())) {
        return Optional.empty();
      }
      return Optional.of(response.getToken());
    } catch (Exception e) {
      log.warn(
          "Unable to issue execution token for subject {}; proceeding without one", subject, e);
      return Optional.empty();
    }
  }
}
