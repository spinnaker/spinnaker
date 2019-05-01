/*
 *
 *  * Copyright 2019 Google, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce.SetStatefulDiskTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SetStatefulDiskStage implements StageDefinitionBuilder {

  private final DynamicConfigService dynamicConfigService;

  @Autowired
  public SetStatefulDiskStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("setStatefulDisk", SetStatefulDiskTask.class);

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask.class);
    }
  }

  @Data
  public static class StageData {

    public String accountName;
    public String serverGroupName;
    public String region;
    public String deviceName;
  }
}
