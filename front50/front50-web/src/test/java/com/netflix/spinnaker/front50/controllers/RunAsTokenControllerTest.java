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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.ServiceCallerContext;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenMinter;
import com.nimbusds.jose.jwk.JWKSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RunAsTokenControllerTest {

  private final SpinnakerTokenMinter minter = mock(SpinnakerTokenMinter.class);
  private final ServiceAccountDAO serviceAccountDAO = mock(ServiceAccountDAO.class);
  private final PipelineDAO pipelineDAO = mock(PipelineDAO.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    ServiceCallerContext.clear();
  }

  @SuppressWarnings("unchecked")
  private RunAsTokenController controller(boolean authzEnabled) {
    ObjectProvider<SpinnakerTokenMinter> minterProvider = mock(ObjectProvider.class);
    when(minterProvider.getIfAvailable()).thenReturn(minter);
    ObjectProvider<UserRolesResolver> resolverProvider = mock(ObjectProvider.class);
    when(resolverProvider.getIfAvailable()).thenReturn(null);

    lenient().when(minter.mint(any(SpinnakerTokenClaims.class))).thenReturn("minted-token");
    lenient()
        .when(minter.mint(any(String.class), anyList(), anyBoolean(), anyBoolean()))
        .thenReturn("minted-token");

    AuthorizationProperties authz = new AuthorizationProperties();
    authz.setEnabled(authzEnabled);

    return new RunAsTokenController(
        Optional.of(serviceAccountDAO),
        Optional.of(pipelineDAO),
        minterProvider,
        resolverProvider,
        new JWKSet(),
        authz);
  }

  /** Simulate the inbound filter having authenticated a verified identity token (Orca's path). */
  private void withVerifiedIdentityToken() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("alice@corp", "n/a", "ROLE_USER"));
  }

  /** Simulate the s2s filter having authenticated Echo as the calling service (Echo's path). */
  private void withTrustedServiceCaller() {
    ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "test"));
  }

  /** Simulate the s2s filter having authenticated Orca as the calling service. */
  private void withOrcaCaller() {
    ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ORCA, "CN=orca", "test"));
  }

  @Test
  void initialMintRejectsUnprovenCallerWhenAuthzEnabled() {
    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");
    request.setPipelineId("p1");

    // No identity token in the SecurityContext and no authenticated service caller -> fail closed.
    assertThatThrownBy(() -> controller(true).mintRunAsToken(request))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void initialMintRejectsMissingPipelineId() {
    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");

    withVerifiedIdentityToken();
    assertThatThrownBy(() -> controller(true).mintRunAsToken(request))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void initialMintRejectsAccountNotConfiguredOnPipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.setServiceAccount("other@corp");
    when(pipelineDAO.findById("p1")).thenReturn(pipeline);
    withTrustedServiceCaller();

    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");
    request.setPipelineId("p1");

    assertThatThrownBy(() -> controller(true).mintRunAsToken(request))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void initialMintSucceedsWithTrustedServiceCaller() {
    ServiceAccount sa = new ServiceAccount();
    sa.setName("robot@corp");
    sa.setMemberOf(List.of("team-a"));
    when(serviceAccountDAO.findById("robot@corp")).thenReturn(sa);

    Pipeline pipeline = new Pipeline();
    Trigger trigger = new Trigger();
    trigger.put("runAsUser", "robot@corp");
    pipeline.setTriggers(List.of(trigger));
    when(pipelineDAO.findById("p1")).thenReturn(pipeline);

    // Echo's path: authenticated as a trusted internal service caller (mTLS/mesh/k8s).
    withTrustedServiceCaller();

    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");
    request.setPipelineId("p1");

    RunAsTokenResponse response = controller(true).mintRunAsToken(request);
    assertThat(response.getSubject()).isEqualTo("robot@corp");
    assertThat(response.getRoles()).containsExactly("team-a");
  }

  @Test
  void initialMintSucceedsWithVerifiedIdentityToken() {
    ServiceAccount sa = new ServiceAccount();
    sa.setName("robot@corp");
    sa.setMemberOf(List.of("team-a"));
    when(serviceAccountDAO.findById("robot@corp")).thenReturn(sa);

    Pipeline pipeline = new Pipeline();
    pipeline.setServiceAccount("robot@corp");
    when(pipelineDAO.findById("p1")).thenReturn(pipeline);

    // Orca's path: a verified identity token is present, no service caller needed.
    withVerifiedIdentityToken();

    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");
    request.setPipelineId("p1");

    RunAsTokenResponse response = controller(true).mintRunAsToken(request);
    assertThat(response.getSubject()).isEqualTo("robot@corp");
    assertThat(response.getRoles()).containsExactly("team-a");
  }

  @Test
  void initialMintIsPermissiveWhenAuthzDisabled() {
    when(serviceAccountDAO.findById(eq("robot@corp"))).thenThrow(new NotFoundException("none"));
    RunAsTokenRequest request = new RunAsTokenRequest();
    request.setServiceAccount("robot@corp");

    // Legacy rollout posture: authz disabled -> mint without any caller proof.
    RunAsTokenResponse response = controller(false).mintRunAsToken(request);
    assertThat(response.getToken()).isEqualTo("minted-token");
  }

  private static ExecutionTokenRequest executionRequest(
      String subject, List<String> roles, boolean admin) {
    ExecutionTokenRequest request = new ExecutionTokenRequest();
    request.setSubject(subject);
    request.setRoles(roles);
    request.setAdmin(admin);
    return request;
  }

  @Test
  void issueExecutionTokenTrustsRelayedUserRoles() {
    // A user subject's roles cannot be re-resolved (EXTERNAL group membership), so they are relayed
    // by Orca in the body and minted verbatim, authorized by the Orca s2s caller identity.
    withOrcaCaller();

    RunAsTokenResponse response =
        controller(true)
            .issueExecutionToken(executionRequest("alice@corp", List.of("dev", "ops"), true));

    assertThat(response.getSubject()).isEqualTo("alice@corp");
    assertThat(response.getRoles()).containsExactly("dev", "ops");
    assertThat(response.getToken()).isEqualTo("minted-token");
  }

  @Test
  void issueExecutionTokenReResolvesServiceAccountWithEmptyRoles() {
    // A known service account carrying no roles (run-as cold-start continuation) has its roles
    // re-resolved from Front50's memberOf for freshness.
    ServiceAccount sa = new ServiceAccount();
    sa.setName("robot@corp");
    sa.setMemberOf(List.of("team-a"));
    when(serviceAccountDAO.findById("robot@corp")).thenReturn(sa);
    withOrcaCaller();

    RunAsTokenResponse response =
        controller(true).issueExecutionToken(executionRequest("robot@corp", List.of(), false));

    assertThat(response.getSubject()).isEqualTo("robot@corp");
    assertThat(response.getRoles()).containsExactly("team-a");
  }

  @Test
  void issueExecutionTokenRejectsWhenNoOrcaCaller() {
    // Fail closed: no authenticated Orca caller (e.g. authz.s2s disabled) -> deny.
    assertThatThrownBy(
            () ->
                controller(true)
                    .issueExecutionToken(executionRequest("alice@corp", List.of(), false)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void issueExecutionTokenRejectsNonOrcaCaller() {
    // Only Orca may relay an execution identity; Echo (or anyone else) is denied.
    withTrustedServiceCaller(); // ECHO

    assertThatThrownBy(
            () ->
                controller(true)
                    .issueExecutionToken(executionRequest("alice@corp", List.of(), false)))
        .isInstanceOf(AccessDeniedException.class);
  }
}
