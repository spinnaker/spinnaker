/*
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DeployCloudrunManifestTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PromoteManifestKatoOutputsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeployCloudrunManifestStageTest {

  @Test
  void refreshesTheServerGroupCacheAfterTheDeployAndBeforeLaterStagesNeedTheRevision() {
    DeployCloudrunManifestStage stage = new DeployCloudrunManifestStage(mock(OortService.class));

    TaskNode.TaskGraph graph =
        TaskNode.build(
            TaskNode.GraphType.FULL, builder -> stage.taskGraph(new StageExecutionImpl(), builder));

    List<Class<?>> tasks = new ArrayList<>();
    graph.forEach(node -> tasks.add(((TaskNode.TaskDefinition) node).getImplementingClass()));
    assertThat(tasks)
        .containsSubsequence(
            DeployCloudrunManifestTask.class,
            MonitorKatoTask.class,
            ServerGroupCacheForceRefreshTask.class,
            PromoteManifestKatoOutputsTask.class);
    int deploy = tasks.indexOf(DeployCloudrunManifestTask.class);
    int refresh = tasks.indexOf(ServerGroupCacheForceRefreshTask.class);
    assertThat(tasks.subList(deploy, refresh)).contains(MonitorKatoTask.class);
  }
}
