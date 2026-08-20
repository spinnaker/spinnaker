/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import com.netflix.spinnaker.gate.mcp.support.McpAccessGuard;
import com.netflix.spinnaker.gate.mcp.support.McpReadOnlyModeException;
import com.netflix.spinnaker.gate.mcp.support.OrchestrationJobs;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentToolsTest {

  @Mock private ClouddriverServiceSelector clouddriverServiceSelector;
  @Mock private TaskService taskService;

  private DeploymentTools deploymentTools;
  private McpServerProperties properties;

  @BeforeEach
  void setUp() {
    properties = new McpServerProperties();
    properties.setReadOnly(false);
    OrchestrationJobs orchestrationJobs =
        new OrchestrationJobs(taskService, new McpAccessGuard(properties));
    deploymentTools = new DeploymentTools(clouddriverServiceSelector, orchestrationJobs);
  }

  @Test
  void deployAwsServerGroupBuildsCreateServerGroupJob() {
    Map<String, Object> taskResult1 = Map.of("id", "task-1", "status", "SUCCEEDED");
    doReturn(taskResult1).when(taskService).createAndWaitForCompletion(anyMap());

    deploymentTools.deployAwsServerGroup(
        "myapp",
        "test",
        "us-east-1",
        "ami-0123456789abcdef0",
        "prod",
        null,
        "m5.large",
        "internal",
        1,
        2,
        3,
        List.of("myapp-sg"),
        List.of("myapp-elb"));

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    List<Map<String, Object>> jobs = (List<Map<String, Object>>) captor.getValue().get("job");
    Map<String, Object> job = jobs.get(0);

    assertThat(job.get("type")).isEqualTo("createServerGroup");
    assertThat(job.get("cloudProvider")).isEqualTo("aws");
    assertThat(job.get("application")).isEqualTo("myapp");
    assertThat(job.get("account")).isEqualTo("test");
    assertThat(job.get("region")).isEqualTo("us-east-1");
    assertThat(job.get("amiName")).isEqualTo("ami-0123456789abcdef0");
    assertThat(job.get("stack")).isEqualTo("prod");
    assertThat(job.get("instanceType")).isEqualTo("m5.large");
    assertThat(job.get("securityGroups")).isEqualTo(List.of("myapp-sg"));
    assertThat(job.get("loadBalancers")).isEqualTo(List.of("myapp-elb"));

    Map<String, Object> capacity = (Map<String, Object>) job.get("capacity");
    assertThat(capacity)
        .containsEntry("min", 1)
        .containsEntry("desired", 2)
        .containsEntry("max", 3);
  }

  @Test
  void deployAwsServerGroupRejectedInReadOnlyMode() {
    properties.setReadOnly(true);

    assertThatThrownBy(
            () ->
                deploymentTools.deployAwsServerGroup(
                    "myapp",
                    "test",
                    "us-east-1",
                    "ami-123",
                    null,
                    null,
                    "m5.large",
                    null,
                    1,
                    1,
                    1,
                    null,
                    null))
        .isInstanceOf(McpReadOnlyModeException.class);
  }

  @Test
  void upsertLoadBalancerMergesExtraFields() {
    Map<String, Object> taskResult2 = Map.of("id", "task-2", "status", "SUCCEEDED");
    doReturn(taskResult2).when(taskService).createAndWaitForCompletion(anyMap());

    deploymentTools.upsertLoadBalancer(
        "myapp", "test", "aws", "us-east-1", "myapp-elb", Map.of("subnetType", "internal"));

    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(taskService).createAndWaitForCompletion(captor.capture());
    List<Map<String, Object>> jobs = (List<Map<String, Object>>) captor.getValue().get("job");
    Map<String, Object> job = jobs.get(0);

    assertThat(job.get("type")).isEqualTo("upsertLoadBalancer");
    assertThat(job.get("name")).isEqualTo("myapp-elb");
    assertThat(job.get("subnetType")).isEqualTo("internal");
  }
}
