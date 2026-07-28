/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.authz.Role;
import com.netflix.spinnaker.security.roles.ExternalUser;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import com.netflix.spinnaker.security.s2s.AllowServiceCallers;
import com.netflix.spinnaker.security.s2s.ServiceCallerContext;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dedicated token endpoint (Component 7). Front50 owns managed service accounts and already
 * trusts both Gate's and its own JWKS, so it is the single token authority for async execution.
 *
 * <p>Two mint operations, with different authorization:
 *
 * <ul>
 *   <li><b>{@code POST /auth/runAsToken}</b> — the initial mint for an automated/event trigger that
 *       has no prior token. This is the one path that can fabricate an identity, so the caller must
 *       prove it is a trusted internal component by <em>either</em> (a) a cryptographically
 *       authenticated service-to-service caller identity ({@code Echo}/{@code Orca}, via {@code
 *       authz.s2s} mTLS / mesh / Kubernetes ServiceAccount token; enforced by {@link
 *       AllowServiceCallers}), <em>or</em> (b) carrying a signature-valid Spinnaker identity token
 *       (verified by the inbound filter into the {@code SecurityContext} — Orca presenting the
 *       parent execution's token). In both cases the requested service account must match the
 *       {@code runAsUser}/service account configured on the saved pipeline, so neither proof can
 *       mint an arbitrary (e.g. more-privileged) subject. Anonymous or arbitrary-subject mints are
 *       refused when authorization is enabled. Echo holds no signing key, so its cold-start mint
 *       requires {@code authz.s2s.enabled=true}.
 *   <li><b>{@code POST /auth/issueExecutionToken}</b> — re-issues a fresh identity token for an
 *       in-flight execution's already-admitted subject (user or SA), carrying the subject + roles
 *       captured at admission. Authorization is the authenticated service-to-service caller
 *       identity: only {@code Orca} may call it (via {@code authz.s2s}; enforced by {@link
 *       AllowServiceCallers} and re-checked fail-closed). Because the caller is authenticated at
 *       the transport, the already-admitted subject/roles are relayed in the request body and Orca
 *       needs no signing key — so the admission-time grant propagates across Orca's async stage
 *       boundaries without a replayable token and without distributing a minting key to Orca.
 * </ul>
 *
 * <p>No shared secret is involved, and no service other than Gate and Front50 holds an
 * identity-token minting key. When authorization is disabled ({@code authz.enabled=false}) the
 * initial mint preserves the legacy permissive behavior so rollout is unaffected; in that mode
 * there is typically no signing key, so no token is minted at all.
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(value = "authz.runas.enabled", matchIfMissing = true)
public class RunAsTokenController {

  private static final Logger log = LoggerFactory.getLogger(RunAsTokenController.class);

  private final Optional<ServiceAccountDAO> serviceAccountDAO;
  private final Optional<PipelineDAO> pipelineDAO;
  private final ObjectProvider<SpinnakerTokenMinter> runAsTokenMinter;
  private final ObjectProvider<UserRolesResolver> userRolesResolver;
  private final JWKSet runAsPublicJwks;
  private final AuthorizationProperties authz;

  /**
   * Services permitted to bootstrap an initial run-as mint (proven via service-to-service auth).
   */
  private static final Set<SpinnakerService> RUN_AS_MINT_CALLERS =
      EnumSet.of(SpinnakerService.ECHO, SpinnakerService.ORCA);

  public RunAsTokenController(
      Optional<ServiceAccountDAO> serviceAccountDAO,
      Optional<PipelineDAO> pipelineDAO,
      ObjectProvider<SpinnakerTokenMinter> runAsTokenMinter,
      ObjectProvider<UserRolesResolver> userRolesResolver,
      JWKSet runAsPublicJwks,
      AuthorizationProperties authz) {
    this.serviceAccountDAO = serviceAccountDAO;
    this.pipelineDAO = pipelineDAO;
    this.runAsTokenMinter = runAsTokenMinter;
    this.userRolesResolver = userRolesResolver;
    this.runAsPublicJwks = runAsPublicJwks;
    this.authz = authz;
  }

  @Operation(summary = "Mint the initial run-as identity token for an automated trigger")
  @PostMapping("/runAsToken")
  @AllowServiceCallers({SpinnakerService.ECHO, SpinnakerService.ORCA})
  public RunAsTokenResponse mintRunAsToken(@RequestBody RunAsTokenRequest request) {
    String serviceAccountName = request.getServiceAccount();
    if (serviceAccountName == null || serviceAccountName.isBlank()) {
      throw new IllegalArgumentException("serviceAccount is required");
    }

    SpinnakerTokenMinter minter = requireMinter();
    authorizeInitialMint(serviceAccountName, request.getPipelineId());

    List<String> roles = resolveRoles(serviceAccountName);
    String token = minter.mint(serviceAccountName, roles, false, false);
    log.debug(
        "Minted initial run-as token for service account {} with roles {}",
        serviceAccountName,
        roles);
    return new RunAsTokenResponse(token, serviceAccountName, roles);
  }

  @Operation(
      summary = "Re-issue an identity token for an in-flight execution's already-admitted subject")
  @PostMapping("/issueExecutionToken")
  @AllowServiceCallers(SpinnakerService.ORCA)
  public RunAsTokenResponse issueExecutionToken(@RequestBody ExecutionTokenRequest request) {
    SpinnakerTokenMinter minter = requireMinter();

    // Authorization is the authenticated service caller: only Orca may relay an already-admitted
    // execution identity. The subject/roles are trusted because they arrive from Orca over an
    // authenticated (mTLS / mesh / Kubernetes ServiceAccount) channel — the s2s caller identity
    // replaces the former signed assertion (and Orca's signing key). Fail closed: because the
    // @AllowServiceCallers aspect is inert when authz.s2s is disabled, require the ORCA caller
    // explicitly here so the body can never be trusted from an unauthenticated caller.
    if (authz.isEnabled() && !hasServiceCaller(SpinnakerService.ORCA)) {
      throw new AccessDeniedException(
          "Execution-token issuance requires an authenticated Orca service caller (authz.s2s).");
    }

    String subject = request.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    List<String> roles = request.getRoles() == null ? new ArrayList<>() : request.getRoles();
    boolean admin = request.isAdmin();
    boolean accountManager = request.isAccountManager();

    // A known service account carrying no roles (e.g. an automated trigger's run-as continuation)
    // gets its roles re-resolved from Front50's own memberOf for freshness; an SA is never
    // admin/account-manager. A user subject's roles are relayed verbatim (they cannot be
    // re-resolved
    // with EXTERNAL group membership).
    if (roles.isEmpty() && isServiceAccount(subject)) {
      roles = resolveRoles(subject);
      admin = false;
      accountManager = false;
    }

    String token =
        minter.mint(
            SpinnakerTokenClaims.builder(subject)
                .roles(roles)
                .admin(admin)
                .accountManager(accountManager)
                .build());
    log.debug("Issued execution token for subject {} with roles {}", subject, roles);
    return new RunAsTokenResponse(token, subject, roles);
  }

  @Operation(summary = "Public JWK set for verifying Front50-minted run-as tokens")
  @GetMapping("/jwks")
  public Map<String, Object> jwks() {
    // Public parts only (the signing key's private material is never serialized here).
    return runAsPublicJwks.toJSONObject();
  }

  private SpinnakerTokenMinter requireMinter() {
    SpinnakerTokenMinter minter = runAsTokenMinter.getIfAvailable();
    if (minter == null) {
      throw new IllegalStateException(
          "No signing key configured (authz.signing.keys); run-as tokens cannot be minted. "
              + "This is expected when authorization is disabled (authz.enabled=false). "
              + "Configure a shared RSA signing key to enable run-as token minting.");
    }
    return minter;
  }

  /**
   * Guards the initial mint: it is the only path that can fabricate an identity from nothing, so
   * the caller must prove it is a trusted internal component. Proof is <em>either</em> a
   * cryptographically authenticated service-to-service caller identity ({@code Echo}/{@code Orca}
   * via {@code authz.s2s} mTLS / mesh / Kubernetes ServiceAccount token) <em>or</em> a
   * signature-valid Spinnaker identity token the inbound filter authenticated into the {@code
   * SecurityContext} (Orca presenting the parent execution's token). In all cases the requested
   * service account must be the one the saved pipeline is configured to run as, so no proof can
   * mint an arbitrary (e.g. more-privileged) subject.
   *
   * <p>Note: Echo no longer signs a shared-key assertion, so authenticating Echo's cold-start mint
   * requires {@code authz.s2s.enabled=true}.
   */
  private void authorizeInitialMint(String serviceAccountName, String pipelineId) {
    if (!authz.isEnabled()) {
      // Authorization disabled: preserve legacy permissive behavior so an in-progress rollout is
      // unaffected. No signing key typically exists in this mode, so requireMinter() will already
      // have rejected the call.
      return;
    }

    if (pipelineId == null || pipelineId.isBlank()) {
      throw new AccessDeniedException("pipelineId is required to mint a run-as token.");
    }

    boolean callerProven = hasTrustedServiceCaller() || hasVerifiedIdentityToken();
    if (!callerProven) {
      throw new AccessDeniedException(
          "Run-as token minting requires an authenticated internal service caller "
              + "(authz.s2s) or a valid Spinnaker identity token when authorization is enabled.");
    }

    if (!isConfiguredRunAsUser(pipelineId, serviceAccountName)) {
      throw new AccessDeniedException(
          "Service account '"
              + serviceAccountName
              + "' is not the configured runAsUser of pipeline '"
              + pipelineId
              + "'.");
    }
  }

  /**
   * Whether the request came from a cryptographically authenticated internal service caller
   * permitted to bootstrap a run-as mint (Echo or Orca). Populated by the service-to-service
   * authentication filter from the caller's mTLS / mesh / Kubernetes ServiceAccount identity; empty
   * when {@code authz.s2s} is disabled.
   */
  private boolean hasTrustedServiceCaller() {
    return ServiceCallerContext.current()
        .map(caller -> RUN_AS_MINT_CALLERS.contains(caller.service()))
        .orElse(false);
  }

  /** Whether the authenticated service-to-service caller is the given Spinnaker service. */
  private boolean hasServiceCaller(SpinnakerService service) {
    return ServiceCallerContext.current().map(caller -> caller.service() == service).orElse(false);
  }

  /**
   * Whether the inbound request carried a signature-valid Spinnaker identity token — the {@code
   * IdentityTokenAuthenticationFilter} populates the {@code SecurityContext} from a verified token,
   * so a non-anonymous authenticated principal means the caller presented one (Orca presenting the
   * parent execution's token as proof of internal origin).
   */
  private boolean hasVerifiedIdentityToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }

  /**
   * Whether {@code serviceAccountName} is the service account / {@code runAsUser} the saved
   * pipeline is configured to run as. This binds a credential-authenticated mint to the pipeline's
   * own configuration, so a leaked credential cannot mint an arbitrary (e.g. more-privileged)
   * subject.
   */
  private boolean isConfiguredRunAsUser(String pipelineId, String serviceAccountName) {
    if (pipelineDAO.isEmpty()) {
      return false;
    }
    Pipeline pipeline;
    try {
      pipeline = pipelineDAO.get().findById(pipelineId);
    } catch (NotFoundException e) {
      return false;
    }
    if (pipeline == null) {
      return false;
    }

    Set<String> configured = new LinkedHashSet<>();
    if (pipeline.getServiceAccount() != null) {
      configured.add(pipeline.getServiceAccount());
    }
    if (pipeline.getTriggers() != null) {
      for (Trigger trigger : pipeline.getTriggers()) {
        Object runAsUser = trigger.get("runAsUser");
        if (runAsUser instanceof String) {
          configured.add((String) runAsUser);
        }
      }
    }
    return configured.stream().anyMatch(serviceAccountName::equalsIgnoreCase);
  }

  private boolean isServiceAccount(String subject) {
    if (serviceAccountDAO.isEmpty()) {
      return false;
    }
    try {
      return serviceAccountDAO.get().findById(subject) != null;
    } catch (NotFoundException e) {
      return false;
    }
  }

  private List<String> resolveRoles(String serviceAccountName) {
    List<String> memberOf = new ArrayList<>();
    if (serviceAccountDAO.isPresent()) {
      try {
        ServiceAccount serviceAccount = serviceAccountDAO.get().findById(serviceAccountName);
        if (serviceAccount != null && serviceAccount.getMemberOf() != null) {
          memberOf.addAll(serviceAccount.getMemberOf());
        }
      } catch (NotFoundException e) {
        log.debug(
            "No managed service account '{}'; minting token with no roles", serviceAccountName);
      }
    }

    UserRolesResolver resolver = userRolesResolver.getIfAvailable();
    if (resolver == null) {
      return memberOf;
    }
    // SAs arrive with externalRoles pre-set; run the same resolve + external-group merge as login.
    ExternalUser user =
        new ExternalUser()
            .setId(serviceAccountName)
            .setExternalRoles(memberOf.stream().map(Role::new).collect(Collectors.toList()));
    return new ArrayList<>(resolver.resolveRoleNames(user));
  }
}
