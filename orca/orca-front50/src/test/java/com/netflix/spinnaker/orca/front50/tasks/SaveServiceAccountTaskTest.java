/*
 * Copyright 2025 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.ServiceAccount;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Response;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class SaveServiceAccountTaskTest {

  private static final String TOKEN = "test-identity-token";

  @Mock private Front50Service front50Service;
  @Mock private SpinnakerTokenVerifier tokenVerifier;

  private final ObjectMapper objectMapper = OrcaObjectMapper.getInstance();
  private final boolean useSharedManagedServiceAccounts = false;
  private final AuthorizationProperties authorizationProperties = new AuthorizationProperties();

  private SaveServiceAccountTask task;
  private PipelineExecutionImpl execution;

  @BeforeEach
  public void setup() {
    task =
        new SaveServiceAccountTask(
            Optional.of(front50Service),
            Optional.of(tokenVerifier),
            Optional.of(authorizationProperties),
            objectMapper,
            useSharedManagedServiceAccounts);

    execution = new PipelineExecutionImpl(ORCHESTRATION, "test-app");
    execution.setTrigger(new DefaultTrigger("manual", null, "testuser@example.com"));

    // The saving user's roles + admin flag are read from the verified identity token on the
    // request.
    AuthenticatedRequest.set(Header.IDENTITY_TOKEN, TOKEN);
  }

  @AfterEach
  public void cleanup() {
    AuthenticatedRequest.clear();
  }

  private void stubUserRoles(List<String> roles) {
    lenient()
        .when(tokenVerifier.verify(TOKEN))
        .thenReturn(SpinnakerTokenClaims.builder("testuser@example.com").roles(roles).build());
  }

  private void stubAdminUser() {
    lenient()
        .when(tokenVerifier.verify(TOKEN))
        .thenReturn(SpinnakerTokenClaims.builder("testuser@example.com").admin(true).build());
  }

  /** Front50 reports no existing managed service accounts, so every save is treated as a change. */
  private void stubNoExistingServiceAccounts() {
    // A retrofit2 mock Call can only be executed once, and the bulk path queries Front50 per
    // pipeline, so answer with a fresh Call on each invocation.
    lenient()
        .when(front50Service.getServiceAccounts())
        .thenAnswer(invocation -> Calls.response(Collections.emptyList()));
  }

  private void stubExistingServiceAccounts(ServiceAccount... serviceAccounts) {
    lenient()
        .when(front50Service.getServiceAccounts())
        .thenAnswer(invocation -> Calls.response(Arrays.asList(serviceAccounts)));
  }

  private static ServiceAccount serviceAccount(String name, List<String> memberOf) {
    ServiceAccount sa = new ServiceAccount();
    sa.setName(name);
    sa.setMemberOf(memberOf);
    return sa;
  }

  @Test
  public void shouldProcessMultiplePipelinesInBulkSaveMode() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    pipelines.add(createPipeline("pipeline-2", "Pipeline 2", List.of("bar")));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    stubUserRoles(List.of("foo", "bar"));
    stubNoExistingServiceAccounts();

    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    verify(front50Service, times(2)).saveServiceAccount(any(ServiceAccount.class));
    verify(front50Service)
        .saveServiceAccount(
            argThat(
                sa ->
                    sa != null
                        && sa.getName().equals("pipeline-1@managed-service-account")
                        && sa.getMemberOf() != null
                        && sa.getMemberOf().contains("foo")));
    verify(front50Service)
        .saveServiceAccount(
            argThat(
                sa ->
                    sa != null
                        && sa.getName().equals("pipeline-2@managed-service-account")
                        && sa.getMemberOf() != null
                        && sa.getMemberOf().contains("bar")));
  }

  @Test
  public void shouldSkipPipelinesWithoutRolesInBulkSaveMode() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    Map<String, Object> pipelineWithoutRoles = new HashMap<>();
    pipelineWithoutRoles.put("application", "orca");
    pipelineWithoutRoles.put("id", "pipeline-2");
    pipelineWithoutRoles.put("name", "Pipeline 2");
    pipelineWithoutRoles.put("stages", List.of());
    // No roles field
    pipelines.add(pipelineWithoutRoles);

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    stubUserRoles(List.of("foo"));
    stubNoExistingServiceAccounts();

    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenReturn(Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    // Should only save service account for pipeline-1
    verify(front50Service, times(1)).saveServiceAccount(any(ServiceAccount.class));
    verify(front50Service)
        .saveServiceAccount(
            argThat(sa -> sa.getName().equals("pipeline-1@managed-service-account")));
    verify(front50Service, never())
        .saveServiceAccount(
            argThat(sa -> sa.getName().equals("pipeline-2@managed-service-account")));
  }

  @Test
  public void shouldFailBulkSaveIfUserLacksAuthorizationForAnyPipeline() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    pipelines.add(createPipeline("pipeline-2", "Pipeline 2", List.of("bar", "baz")));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    // User only has 'foo' and 'bar' roles, missing 'baz'
    stubUserRoles(List.of("foo", "bar"));
    stubNoExistingServiceAccounts();

    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenReturn(Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When / Then
    UserException exception = assertThrows(UserException.class, () -> task.execute(stage));
    assertTrue(
        exception
            .getMessage()
            .contains(
                "User 'testuser@example.com' is not authorized with all roles for pipeline 'Pipeline 2'"));

    // Should have saved the first pipeline before failing
    verify(front50Service, times(1)).saveServiceAccount(any(ServiceAccount.class));
    verify(front50Service)
        .saveServiceAccount(
            argThat(sa -> sa.getName().equals("pipeline-1@managed-service-account")));
  }

  @Test
  public void shouldSkipPipelinesWithUnchangedRolesInBulkSaveMode() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    Map<String, Object> pipeline1 = createPipeline("pipeline-1", "Pipeline 1", List.of("foo"));
    pipeline1.put("serviceAccount", "pipeline-1@managed-service-account");
    Map<String, Object> pipeline2 = createPipeline("pipeline-2", "Pipeline 2", List.of("bar"));
    pipeline2.put("serviceAccount", "pipeline-2@managed-service-account");
    pipelines.add(pipeline1);
    pipelines.add(pipeline2);

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    // Both service accounts already have the correct roles in Front50.
    stubExistingServiceAccounts(
        serviceAccount("pipeline-1@managed-service-account", List.of("foo")),
        serviceAccount("pipeline-2@managed-service-account", List.of("bar")));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    // Should not save any service accounts since roles haven't changed
    verify(front50Service, never()).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldGeneratePipelineIDsForPipelinesWithoutIDsInBulkSaveMode() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    Map<String, Object> pipeline1 = new HashMap<>();
    pipeline1.put("application", "orca");
    pipeline1.put("name", "Pipeline 1");
    pipeline1.put("stages", List.of());
    pipeline1.put("roles", List.of("foo"));
    // No ID

    Map<String, Object> pipeline2 = new HashMap<>();
    pipeline2.put("application", "orca");
    pipeline2.put("name", "Pipeline 2");
    pipeline2.put("stages", List.of());
    pipeline2.put("roles", List.of("bar"));
    // No ID

    pipelines.add(pipeline1);
    pipelines.add(pipeline2);

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    stubUserRoles(List.of("foo", "bar"));
    stubNoExistingServiceAccounts();

    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    // Should save both service accounts with generated UUIDs
    verify(front50Service, times(2)).saveServiceAccount(any(ServiceAccount.class));
    verify(front50Service, times(2))
        .saveServiceAccount(argThat(sa -> sa.getName().endsWith("@managed-service-account")));
  }

  @Test
  public void shouldFailBulkSaveIfFront50ReturnsErrorForAnyPipeline() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    pipelines.add(createPipeline("pipeline-2", "Pipeline 2", List.of("bar")));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    stubUserRoles(List.of("foo", "bar"));
    stubNoExistingServiceAccounts();

    // First pipeline saves successfully, second pipeline fails
    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenReturn(Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")))
        .thenReturn(
            Calls.response(
                Response.error(
                    500, ResponseBody.create(MediaType.parse("application/json"), "{}"))));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.TERMINAL, result.getStatus());
    verify(front50Service, times(2)).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldThrowExceptionIfPipelinesContextMissingInBulkSaveMode() {
    // Given
    StageExecutionImpl stage = new StageExecutionImpl(execution, "savePipeline");
    Map<String, Object> context = new HashMap<>();
    context.put("isBulkSavingPipelines", true);
    // Missing 'pipelines' field
    stage.setContext(context);

    // When / Then
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.execute(stage));
    assertEquals(
        "pipelines context must be provided when saving multiple pipelines",
        exception.getMessage());
    verify(front50Service, never()).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldAllowAdminToBulkSaveAllPipelinesRegardlessOfRoles() throws Exception {
    // Given
    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo", "bar", "baz")));
    pipelines.add(createPipeline("pipeline-2", "Pipeline 2", List.of("qux", "quux")));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    // User is an admin (from the verified token).
    stubAdminUser();
    stubNoExistingServiceAccounts();

    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    verify(front50Service, times(2)).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldReEncodePipelinesWithRewrittenTriggersInBulkSaveMode() throws Exception {
    // Given
    Map<String, Object> pipeline1 =
        createPipelineWithTrigger("pipeline-1", "Pipeline 1", List.of("foo"), null);
    Map<String, Object> pipeline2 =
        createPipelineWithTrigger("pipeline-2", "Pipeline 2", List.of("bar"), null);
    List<Map<String, Object>> pipelines = new ArrayList<>(List.of(pipeline1, pipeline2));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    stubUserRoles(List.of("foo", "bar"));
    stubNoExistingServiceAccounts();
    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then (a) the context carries a Base64 "pipelines" value
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    Object encoded = result.getContext().get("pipelines");
    assertTrue(encoded instanceof String, "expected a re-encoded 'pipelines' string in context");

    // (b) decoding shows each pipeline's triggers had runAsUser set to the generated SA name
    List<Map<String, Object>> decoded = decodePipelines((String) encoded);
    assertEquals(2, decoded.size());
    assertTriggerRunAsUser(decoded.get(0), "pipeline-1@managed-service-account");
    assertTriggerRunAsUser(decoded.get(1), "pipeline-2@managed-service-account");
  }

  @Test
  public void shouldRewriteTriggerRunAsUserEvenWhenRolesUnchangedInBulkSaveMode() throws Exception {
    // Given a pipeline whose roles have NOT changed but that still carries a trigger.
    Map<String, Object> pipeline =
        createPipelineWithTrigger("pipeline-1", "Pipeline 1", List.of("foo"), null);
    pipeline.put("serviceAccount", "pipeline-1@managed-service-account");
    List<Map<String, Object>> pipelines = new ArrayList<>(List.of(pipeline));

    StageExecutionImpl stage = createBulkSaveStage(pipelines);

    // The service account already has the correct roles, so roles are unchanged.
    stubExistingServiceAccounts(
        serviceAccount("pipeline-1@managed-service-account", List.of("foo")));

    // When
    TaskResult result = task.execute(stage);

    // Then no Front50 save happens (roles unchanged) ...
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    verify(front50Service, never()).saveServiceAccount(any(ServiceAccount.class));

    // ... but the trigger runAsUser is still rewritten and the pipeline is present in the output.
    List<Map<String, Object>> decoded =
        decodePipelines((String) result.getContext().get("pipelines"));
    assertEquals(1, decoded.size());
    assertTriggerRunAsUser(decoded.get(0), "pipeline-1@managed-service-account");
  }

  @Test
  public void shouldDenyBulkSaveWhenStrictAndNoVerifiedToken() throws Exception {
    // Given strict fail-closed authorization is enabled but the request carries no verified token.
    authorizationProperties.setEnabled(true);
    authorizationProperties.setStrict(true);
    AuthenticatedRequest.clear();

    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    StageExecutionImpl stage = createBulkSaveStage(pipelines);
    stubNoExistingServiceAccounts();

    // When / Then
    assertThrows(UserException.class, () -> task.execute(stage));
    verify(front50Service, never()).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldAllowBulkSaveWhenNotStrictAndNoVerifiedToken() throws Exception {
    // Given the default permissive posture (strict=false) and no verified token.
    AuthenticatedRequest.clear();

    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    StageExecutionImpl stage = createBulkSaveStage(pipelines);
    stubNoExistingServiceAccounts();
    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then the save proceeds (permissive) even without a verified token.
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    verify(front50Service, times(1)).saveServiceAccount(any(ServiceAccount.class));
  }

  @Test
  public void shouldEvaluateRolesNormallyWithValidTokenRegardlessOfStrict() throws Exception {
    // Given strict is enabled but a valid verified token IS present.
    authorizationProperties.setEnabled(true);
    authorizationProperties.setStrict(true);

    List<Map<String, Object>> pipelines = new ArrayList<>();
    pipelines.add(createPipeline("pipeline-1", "Pipeline 1", List.of("foo")));
    StageExecutionImpl stage = createBulkSaveStage(pipelines);
    stubUserRoles(List.of("foo"));
    stubNoExistingServiceAccounts();
    when(front50Service.saveServiceAccount(any(ServiceAccount.class)))
        .thenAnswer(
            invocation ->
                Calls.response(ResponseBody.create(MediaType.parse("application/json"), "[]")));

    // When
    TaskResult result = task.execute(stage);

    // Then normal role evaluation applies and the authorized user's save succeeds.
    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    verify(front50Service, times(1)).saveServiceAccount(any(ServiceAccount.class));
  }

  // Helper methods

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> decodePipelines(String encoded) throws Exception {
    byte[] json = Base64.getDecoder().decode(encoded);
    return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
  }

  @SuppressWarnings("unchecked")
  private static void assertTriggerRunAsUser(
      Map<String, Object> pipeline, String expectedRunAsUser) {
    List<Map<String, Object>> triggers = (List<Map<String, Object>>) pipeline.get("triggers");
    assertNotNull(triggers, "pipeline should carry triggers");
    assertFalse(triggers.isEmpty(), "pipeline should carry at least one trigger");
    triggers.forEach(t -> assertEquals(expectedRunAsUser, t.get("runAsUser")));
  }

  private Map<String, Object> createPipelineWithTrigger(
      String id, String name, List<String> roles, String runAsUser) {
    Map<String, Object> pipeline = createPipeline(id, name, roles);
    Map<String, Object> trigger = new HashMap<>();
    trigger.put("type", "cron");
    if (runAsUser != null) {
      trigger.put("runAsUser", runAsUser);
    }
    List<Map<String, Object>> triggers = new ArrayList<>();
    triggers.add(trigger);
    pipeline.put("triggers", triggers);
    return pipeline;
  }

  private Map<String, Object> createPipeline(String id, String name, List<String> roles) {
    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("application", "orca");
    pipeline.put("id", id);
    pipeline.put("name", name);
    pipeline.put("stages", List.of());
    pipeline.put("roles", roles);
    return pipeline;
  }

  private StageExecutionImpl createBulkSaveStage(List<Map<String, Object>> pipelines)
      throws Exception {
    StageExecutionImpl stage = new StageExecutionImpl(execution, "savePipeline");
    Map<String, Object> context = new HashMap<>();
    context.put("isBulkSavingPipelines", true);
    String pipelinesJson = objectMapper.writeValueAsString(pipelines);
    context.put(
        "pipelines",
        Base64.getEncoder().encodeToString(pipelinesJson.getBytes(StandardCharsets.UTF_8)));
    stage.setContext(context);
    return stage;
  }
}
