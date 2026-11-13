/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable aw or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cloudrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for UpsertCloudrunLoadBalancersTaskTest */
@ExtendWith(MockitoExtension.class)
public class UpsertCloudrunLoadBalancersTaskTest {

  private UpsertCloudrunLoadBalancersTask task;

  private TaskId taskId;

  private KatoService kato;

  private TargetServerGroupResolver resolver;

  @BeforeEach
  void setUp() {
    kato = mock(KatoService.class);
    resolver = mock(TargetServerGroupResolver.class);
    task = new UpsertCloudrunLoadBalancersTask(kato, resolver);
    taskId = new TaskId(UUID.randomUUID().toString());
  }

  @Test
  void upsertLoadBalancersWithNoLoadBalancers() {

    StageExecution stage = new StageExecutionImpl();
    List operations = new ArrayList();
    when(kato.requestOperations("cloudrun", operations)).thenReturn(taskId);
    stage.setType("");
    Map<String, Object> contextMap = new HashMap<>();
    stage.setContext(contextMap);
    contextMap.put("cloudProvider", "cloudrun");
    contextMap.put("loadBalancers", new ArrayList<>());
    TaskResult result = task.execute(stage);
    assertThat((result.getStatus()).name()).isEqualTo("SUCCEEDED");
    assertThat((result.getContext()).get("kato.result.expected")).isEqualTo(false);
  }

  @Test
  void upsertLoadBalancersWithTwoLoadBalancers() {

    StageExecution stage = new StageExecutionImpl();
    List operations = new ArrayList();
    stage.setType("");
    Map<String, Object> contextMap = new HashMap<>();
    stage.setContext(contextMap);
    List loadBalancersList = new ArrayList();
    contextMap.put("cloudProvider", "cloudrun");
    contextMap.put("loadBalancers", loadBalancersList);
    Map<String, Object> loadBalancersMap = new HashMap<>();
    loadBalancersMap.put("credentials", "my-cloudrun-account");
    loadBalancersMap.put("region", "us-central1");
    List allocationDescriptionsList = new ArrayList();
    Map<String, Object> allocationDescriptionsMap = new HashMap<>();
    Map<String, Object> allocationDescriptionsMap1 = new HashMap<>();
    allocationDescriptionsMap1.put("cluster", "testeditlb005-s1-d1");
    allocationDescriptionsMap1.put("percent", 98);
    allocationDescriptionsMap1.put("target", "current_asg_dynamic");
    allocationDescriptionsList.add(allocationDescriptionsMap1);
    Map<String, Object> allocationDescriptionsMap2 = new HashMap<>();
    allocationDescriptionsMap2.put("cluster", "testeditlb005-s1-d1");
    allocationDescriptionsMap2.put("percent", 2);
    allocationDescriptionsMap2.put("target", "ancestor_asg_dynamic");
    allocationDescriptionsList.add(allocationDescriptionsMap2);
    allocationDescriptionsMap.put("allocationDescriptions", allocationDescriptionsList);
    loadBalancersMap.put("splitDescription", allocationDescriptionsMap);
    loadBalancersList.add(loadBalancersMap);
    Map<String, Object> operation = new HashMap();
    operation.put("upsertLoadBalancer", loadBalancersMap);
    operations.add(operation);
    when(resolver.resolveByParams(any())).thenReturn(List.of(targetServerGroup("app-v001")));
    when(kato.requestOperations("cloudrun", operations)).thenReturn(taskId);
    TaskResult result = task.execute(stage);
    assertThat((result.getStatus()).name()).isEqualTo("SUCCEEDED");
    assertThat((result.getContext()).get("kato.result.expected")).isEqualTo(true);
  }

  @Test
  void failsWhenTheTargetRevisionCannotBeResolved() {
    when(resolver.resolveByParams(any())).thenReturn(List.of());
    StageExecution stage = stageWithSingleAllocation("current_asg_dynamic");

    assertThatThrownBy(() -> task.execute(stage))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app-stack");
    verifyNoInteractions(kato);
  }

  @Test
  void prefersTheRevisionDeployedByAnUpstreamStage() {
    StageExecution stage = stageWithSingleAllocation("current_asg_dynamic");
    StageExecution deploy = mock(StageExecution.class);
    lenient().when(deploy.getType()).thenReturn("deployCloudrunManifest");
    when(deploy.getContext())
        .thenReturn(
            Map.of(
                "account",
                "my-cloudrun-account",
                "deploy.server.groups",
                Map.of("us-central1", List.of("app-stack-v003"))));
    StageExecution spy = spy(stage);
    doReturn(List.of(deploy)).when(spy).ancestors();
    when(kato.requestOperations(any(), any())).thenReturn(taskId);

    task.execute(spy);

    Map loadBalancer = (Map) ((List) spy.getContext().get("loadBalancers")).get(0);
    Map allocation =
        (Map)
            ((List) ((Map) loadBalancer.get("splitDescription")).get("allocationDescriptions"))
                .get(0);
    assertThat(allocation.get("revisionName")).isEqualTo("app-stack-v003");
    verifyNoInteractions(resolver);
  }

  @Test
  void usesTheNewestDeployedRevisionWhenSeveralWereReported() {
    assertThat(
            resolveWithUpstreamDeploy(
                "my-cloudrun-account",
                Map.of("us-central1", List.of("app-stack-v002", "app-stack-v003"))))
        .isEqualTo("app-stack-v003");
  }

  @Test
  void fallsBackToTheCacheWhenTheUpstreamDeployReportedNoRevision() {
    // e.g. an older clouddriver that does not report the revision it deployed
    assertThat(resolveWithUpstreamDeploy("my-cloudrun-account", null)).isEqualTo("from-cache-v001");
    assertThat(resolveWithUpstreamDeploy("my-cloudrun-account", Map.of()))
        .isEqualTo("from-cache-v001");
  }

  @Test
  void ignoresUpstreamDeploysForOtherRegionsClustersOrAccounts() {
    assertThat(
            resolveWithUpstreamDeploy(
                "my-cloudrun-account", Map.of("europe-west1", List.of("app-stack-v003"))))
        .isEqualTo("from-cache-v001");
    assertThat(
            resolveWithUpstreamDeploy(
                "my-cloudrun-account", Map.of("us-central1", List.of("other-stack-v003"))))
        .isEqualTo("from-cache-v001");
    assertThat(
            resolveWithUpstreamDeploy(
                "another-account", Map.of("us-central1", List.of("app-stack-v003"))))
        .isEqualTo("from-cache-v001");
  }

  @Test
  void onlyUsesTheUpstreamDeployForTheNewestTarget() {
    StageExecution stage = stageWithSingleAllocation("ancestor_asg_dynamic");
    stubUpstreamDeploy(
        stage, "my-cloudrun-account", Map.of("us-central1", List.of("app-stack-v003")));
    when(resolver.resolveByParams(any())).thenReturn(List.of(targetServerGroup("from-cache-v001")));
    when(kato.requestOperations(any(), any())).thenReturn(taskId);

    task.execute(stage);

    assertThat(allocationOf(stage).get("revisionName")).isEqualTo("from-cache-v001");
  }

  private String resolveWithUpstreamDeploy(
      String deployAccount, Map<String, List<String>> deployed) {
    StageExecution stage = stageWithSingleAllocation("current_asg_dynamic");
    stage = stubUpstreamDeploy(stage, deployAccount, deployed);
    lenient()
        .when(resolver.resolveByParams(any()))
        .thenReturn(List.of(targetServerGroup("from-cache-v001")));
    when(kato.requestOperations(any(), any())).thenReturn(taskId);
    task.execute(stage);
    return (String) allocationOf(stage).get("revisionName");
  }

  private StageExecution stubUpstreamDeploy(
      StageExecution stage, String account, Map<String, List<String>> deployed) {
    StageExecution deploy = mock(StageExecution.class);
    lenient().when(deploy.getType()).thenReturn("deployCloudrunManifest");
    Map<String, Object> context = new HashMap<>();
    context.put("account", account);
    if (deployed != null) {
      context.put("deploy.server.groups", deployed);
    }
    lenient().when(deploy.getContext()).thenReturn(context);
    StageExecution spied = spy(stage);
    lenient().doReturn(List.of(deploy)).when(spied).ancestors();
    // allocationOf reads the spy's context, which is the original stage's map
    return spied;
  }

  @Test
  void explicitTargetRevisionWinsOverEverythingElse() {
    StageExecution stage = stageWithSingleAllocation("current_asg_dynamic");
    allocationOf(stage).put("targetRevision", "app-stack-v007");
    allocationOf(stage).put("revisionName", "app-stack-v002");
    when(kato.requestOperations(any(), any())).thenReturn(taskId);

    task.execute(stage);

    assertThat(allocationOf(stage).get("revisionName")).isEqualTo("app-stack-v007");
    verifyNoInteractions(resolver);
  }

  @Test
  void failsWhenTheTargetRevisionExpressionWasNotEvaluated() {
    StageExecution stage = stageWithSingleAllocation("current_asg_dynamic");
    allocationOf(stage).put("targetRevision", "${missing}");

    assertThatThrownBy(() -> task.execute(stage)).hasMessageContaining("targetRevision");
    verifyNoInteractions(kato);
  }

  private Map allocationOf(StageExecution stage) {
    Map loadBalancer = (Map) ((List) stage.getContext().get("loadBalancers")).get(0);
    return (Map)
        ((List) ((Map) loadBalancer.get("splitDescription")).get("allocationDescriptions")).get(0);
  }

  private StageExecution stageWithSingleAllocation(String target) {
    StageExecution stage = new StageExecutionImpl();
    stage.setType("");
    Map<String, Object> allocation = new HashMap<>();
    allocation.put("cluster", "app-stack");
    allocation.put("percent", 100);
    allocation.put("target", target);
    Map<String, Object> loadBalancer = new HashMap<>();
    loadBalancer.put("credentials", "my-cloudrun-account");
    loadBalancer.put("region", "us-central1");
    loadBalancer.put(
        "splitDescription", Map.of("allocationDescriptions", new ArrayList<>(List.of(allocation))));
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "cloudrun");
    context.put("loadBalancers", new ArrayList<>(List.of(loadBalancer)));
    stage.setContext(context);
    return stage;
  }

  private TargetServerGroup targetServerGroup(String name) {
    return new TargetServerGroup(Map.of("name", name));
  }
}
