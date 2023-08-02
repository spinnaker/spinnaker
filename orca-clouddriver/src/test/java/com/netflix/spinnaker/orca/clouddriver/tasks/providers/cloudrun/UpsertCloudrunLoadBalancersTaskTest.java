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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
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
    when(kato.requestOperations("cloudrun", operations)).thenReturn(taskId);
    TaskResult result = task.execute(stage);
    assertThat((result.getStatus()).name()).isEqualTo("SUCCEEDED");
    assertThat((result.getContext()).get("kato.result.expected")).isEqualTo(true);
  }
}
