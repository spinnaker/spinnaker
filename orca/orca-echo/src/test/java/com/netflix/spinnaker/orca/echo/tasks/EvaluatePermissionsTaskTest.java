/*
 * Copyright 2025 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluatePermissionsTaskTest {

  private static final String TOKEN = "signed-identity-token";

  @Mock private SpinnakerTokenVerifier tokenVerifier;

  private final AuthorizationProperties authorizationProperties = new AuthorizationProperties();

  private EvaluatePermissionsTask task;

  @BeforeEach
  void setUp() {
    task =
        new EvaluatePermissionsTask(
            Optional.of(tokenVerifier), Optional.of(authorizationProperties));
    AuthenticatedRequest.setUser("testuser");
  }

  @AfterEach
  void tearDown() {
    AuthenticatedRequest.clear();
  }

  private StageExecutionImpl stageRequiring(String... requiredGroups) {
    StageExecutionImpl stage = new StageExecutionImpl();
    Map<String, Object> context = new HashMap<>();
    context.put("requiredGroups", Arrays.asList(requiredGroups));
    stage.setContext(context);
    return stage;
  }

  private SpinnakerTokenClaims claims(boolean admin, List<String> roles) {
    return SpinnakerTokenClaims.builder("testuser").roles(roles).admin(admin).build();
  }

  @Test
  void shouldSucceedWhenNoRequiredGroupsAreSpecified() {
    StageExecutionImpl stage = new StageExecutionImpl();
    stage.setContext(new HashMap<>());

    TaskResult result = task.execute(stage);

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(true);
    assertThat(result.getOutputs().get("reason")).isEqualTo("No required groups specified");
  }

  @Test
  void shouldSkipEvaluationWhenNoVerifierIsConfigured() {
    // permissive: no verifier wired at all
    EvaluatePermissionsTask taskWithoutVerifier =
        new EvaluatePermissionsTask(Optional.empty(), Optional.of(authorizationProperties));

    TaskResult result = taskWithoutVerifier.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason")).isEqualTo("No verified identity token available");
  }

  @Test
  void shouldDenyWhenStrictAndNoVerifiedToken() {
    // strict fail-closed enabled, but the request carries no verified identity token
    authorizationProperties.setEnabled(true);
    authorizationProperties.setStrict(true);

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.TERMINAL);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason"))
        .isEqualTo("No verified identity token available and authz.strict is enabled");
  }

  @Test
  void shouldStayPermissiveWhenEnabledButNotStrictAndNoVerifiedToken() {
    // authz enabled but strict off -> preserve permissive rollout behavior
    authorizationProperties.setEnabled(true);
    authorizationProperties.setStrict(false);

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason")).isEqualTo("No verified identity token available");
  }

  @Test
  void shouldEvaluateGroupsNormallyWithValidTokenRegardlessOfStrict() {
    // strict enabled, but a valid verified token is present -> normal group evaluation
    authorizationProperties.setEnabled(true);
    authorizationProperties.setStrict(true);
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN))
        .thenReturn(claims(false, Arrays.asList("group1", "othergroup")));

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(true);
    assertThat(result.getOutputs().get("reason")).isEqualTo("User has required group membership");
  }

  @Test
  void shouldSkipEvaluationWhenNoTokenIsPresent() {
    // permissive: verifier present, but the request carried no identity token
    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason")).isEqualTo("No verified identity token available");
  }

  @Test
  void shouldSkipEvaluationWhenTokenIsInvalid() {
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN)).thenThrow(new TokenValidationException("bad token"));

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason")).isEqualTo("No verified identity token available");
  }

  @Test
  void shouldSucceedWhenUserIsAdmin() {
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN)).thenReturn(claims(true, List.of()));

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(true);
    assertThat(result.getOutputs().get("reason")).isEqualTo("User is an administrator");
  }

  @Test
  void shouldSucceedWhenUserHasRequiredGroup() {
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN))
        .thenReturn(claims(false, Arrays.asList("group1", "othergroup")));

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(true);
    assertThat(result.getOutputs().get("reason")).isEqualTo("User has required group membership");
  }

  @Test
  void shouldFailWhenUserDoesNotHaveRequiredGroup() {
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN))
        .thenReturn(claims(false, Arrays.asList("othergroup", "anothergroup")));

    TaskResult result = task.execute(stageRequiring("group1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.TERMINAL);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(false);
    assertThat(result.getOutputs().get("reason"))
        .isEqualTo("User does not have required group membership");
  }

  @Test
  void shouldHandleCaseInsensitiveGroupComparison() {
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
    when(tokenVerifier.verify(TOKEN))
        .thenReturn(claims(false, Arrays.asList("group1", "othergroup")));

    TaskResult result = task.execute(stageRequiring("GROUP1", "group2"));

    assertThatObject(result.getStatus()).isSameAs(ExecutionStatus.SUCCEEDED);
    assertThat(result.getOutputs().get("permissionsEvaluated")).isEqualTo(true);
    assertThat(result.getOutputs().get("authorized")).isEqualTo(true);
    assertThat(result.getOutputs().get("reason")).isEqualTo("User has required group membership");
  }
}
