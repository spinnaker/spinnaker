/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.proxmox;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProxmoxServerGroupCreatorTest {

  private final ProxmoxServerGroupCreator creator = new ProxmoxServerGroupCreator();

  @Test
  void reportsProxmoxMetadata() {
    assertThat(creator.getCloudProvider()).isEqualTo("proxmox");
    assertThat(creator.isKatoResultExpected()).isFalse();
    assertThat(creator.getHealthProviderName()).contains("Proxmox");
  }

  @Test
  void buildsOperationFromClusterContext() {
    Map<String, Object> cluster = new HashMap<>();
    cluster.put("application", "myapp");
    cluster.put("account", "jm-pve");
    cluster.put("templateVmid", 9000);

    Map<String, Object> context = new HashMap<>();
    context.put("cluster", cluster);
    context.put("unrelated", "value");

    List<Map> operations = creator.getOperations(createStage(context));

    assertThat(operations).hasSize(1);
    Map<String, Object> operation =
        (Map<String, Object>) operations.get(0).get(ProxmoxServerGroupCreator.OPERATION);
    assertThat(operation).containsEntry("templateVmid", 9000).doesNotContainKey("unrelated");
  }

  @Test
  void buildsOperationFromBareContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("application", "myapp");
    context.put("account", "jm-pve");
    context.put("templateVmid", 9000);

    List<Map> operations = creator.getOperations(createStage(context));

    assertThat(operations).hasSize(1);
    Map<String, Object> operation =
        (Map<String, Object>) operations.get(0).get(ProxmoxServerGroupCreator.OPERATION);
    assertThat(operation).containsEntry("templateVmid", 9000).containsEntry("account", "jm-pve");
  }

  private StageExecution createStage(Map<String, Object> context) {
    PipelineExecution pipeline = new PipelineExecutionImpl(PIPELINE, "1", "myapp");
    return new StageExecutionImpl(pipeline, "createServerGroup", context);
  }
}
