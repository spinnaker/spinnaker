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

package com.netflix.spinnaker.echo.pipelinetriggers.runas;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

/**
 * Obtains short-lived, signed run-as identity tokens from Front50's mint/exchange endpoint and
 * propagates them on the current thread's authentication context (Component 7).
 *
 * <p>For run-as, rather than resolving the service account's roles remotely (or holding a signing
 * key), Echo exchanges the service-account name for a token Front50 mints from its own
 * managed-service-account store. The token is stamped into {@link
 * com.netflix.spinnaker.kork.common.Header#IDENTITY_TOKEN}, which the standard {@code
 * SpinnakerRequestHeaderInterceptor} then forwards on the outbound Orca call (any {@code
 * X-SPINNAKER-*} MDC entry is propagated). Echo re-mints at each trigger attempt (stage boundary)
 * so tokens stay short-lived and roles are re-resolved.
 *
 * <p>The mint is the <em>initial</em> bootstrap of an automated-trigger identity (there is no prior
 * token to refresh), so it is the one path that must authenticate the caller. Echo holds <b>no</b>
 * signing key: it proves it is a trusted internal caller via service-to-service caller
 * authentication ({@code authz.s2s}: mTLS / mesh / Kubernetes ServiceAccount token), and Front50
 * confirms the requested account is the pipeline's configured {@code runAsUser}.
 *
 * <p>Failures are non-fatal: when authorization is disabled ({@code authz.enabled} default off) a
 * missing token simply means downstream services fall back to the unsigned {@code X-SPINNAKER-USER}
 * header, preserving today's behavior during rollout.
 */
@Slf4j
public class RunAsTokenService {

  static final String ANONYMOUS = "anonymous";

  private final RunAsTokenClient runAsTokenClient;

  public RunAsTokenService(RunAsTokenClient runAsTokenClient) {
    this.runAsTokenClient = runAsTokenClient;
  }

  /**
   * Mint the initial run-as token for the given service account from Front50.
   *
   * @param serviceAccount the managed service account to run as
   * @param pipelineId the id of the pipeline being triggered (Front50 binds the mint to this
   *     pipeline's configured runAsUser)
   * @return the signed token, or empty if minting was skipped (anonymous/blank user) or failed
   */
  public Optional<String> mintRunAsToken(String serviceAccount, String pipelineId) {
    if (StringUtils.isBlank(serviceAccount) || ANONYMOUS.equalsIgnoreCase(serviceAccount)) {
      // Nothing to vouch for; downstream treats the request as anonymous.
      return Optional.empty();
    }
    try {
      RunAsTokenResponse response =
          AuthenticatedRequest.allowAnonymous(
              () ->
                  Retrofit2SyncCall.execute(
                      runAsTokenClient.mintRunAsToken(
                          new RunAsTokenRequest(serviceAccount, pipelineId))));
      if (response == null || StringUtils.isBlank(response.getToken())) {
        log.warn("Front50 returned no run-as token for service account {}", serviceAccount);
        return Optional.empty();
      }
      return Optional.of(response.getToken());
    } catch (Exception e) {
      log.error("Unable to mint run-as token for service account {}", serviceAccount, e);
      return Optional.empty();
    }
  }

  /**
   * Mint a run-as token for the service account and stamp it onto the current thread's {@link
   * Header#IDENTITY_TOKEN} so it propagates on the next outbound call.
   *
   * @param serviceAccount the managed service account to run as
   * @param pipelineId the id of the pipeline being triggered
   * @return true if a token was minted and set, false otherwise
   */
  public boolean propagateRunAsToken(String serviceAccount, String pipelineId) {
    if (StringUtils.isBlank(serviceAccount) || ANONYMOUS.equalsIgnoreCase(serviceAccount)) {
      // Genuinely anonymous trigger (no service account to run as); leave the context untouched.
      return false;
    }
    Optional<String> token = mintRunAsToken(serviceAccount, pipelineId);
    token.ifPresent(t -> AuthenticatedRequest.set(Header.IDENTITY_TOKEN, t));
    // We are running as a concrete service account, so this call is NOT anonymous. Strip any
    // ambient X-SPINNAKER-ANONYMOUS marker that may be present on this thread — inherited from an
    // anonymous inbound event, a reused async (events/executor) thread, or restored by the run-as
    // mint's allowAnonymous() wrapper — so downstream authenticates as the service account rather
    // than treating the trigger (and the events it propagates) as anonymous.
    MDC.remove(Header.XSpinnakerAnonymous);
    return token.isPresent();
  }
}
