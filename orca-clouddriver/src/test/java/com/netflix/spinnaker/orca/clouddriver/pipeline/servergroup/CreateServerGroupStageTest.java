/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.clouddriver.FeaturesService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.RollbackClusterStage;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.CheckIfApplicationExistsForServerGroupTask;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CreateServerGroupStageTest {
  private DynamicConfigService dynamicConfigService;
  CreateServerGroupStage createServerGroupStage;

  @BeforeEach
  public void setup() {
    FeaturesService featuresService = mock(FeaturesService.class);
    RollbackClusterStage rollbackClusterStage = mock(RollbackClusterStage.class);
    DestroyServerGroupStage destroyServerGroupStage = mock(DestroyServerGroupStage.class);
    dynamicConfigService = mock(DynamicConfigService.class);
    createServerGroupStage =
        new CreateServerGroupStage(
            featuresService, rollbackClusterStage, destroyServerGroupStage, dynamicConfigService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testPresenceOfCheckIfApplicationExistsForServerGroupTask(boolean isTaskEnabled) {
    when(dynamicConfigService.isEnabled(
            "stages.create-server-group-stage.check-if-application-exists", false))
        .thenReturn(isTaskEnabled);
    Map<String, Class> optionalTasks = createServerGroupStage.getOptionalPreValidationTasks();

    if (isTaskEnabled) {
      assertFalse(optionalTasks.isEmpty());
      assertThat(optionalTasks.size()).isEqualTo(1);
      assertTrue(
          optionalTasks.containsKey(CheckIfApplicationExistsForServerGroupTask.getTaskName()));
      assertTrue(optionalTasks.containsValue(CheckIfApplicationExistsForServerGroupTask.class));
    } else {
      assertTrue(optionalTasks.isEmpty());
    }
  }
}
