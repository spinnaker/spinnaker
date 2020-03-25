/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.tencentcloud;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.kato.pipeline.support.AsgDescriptionSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertTencentCloudScheduledActionsTask implements Task {

  @Autowired private KatoService kato;

  @Override
  public TaskResult execute(StageExecution stage) {
    Map<String, Map> map = new HashMap<>();
    map.put("upsertTencentCloudScheduledActionsDescription", stage.getContext());
    Collection<Map<String, Map>> ops = new ArrayList<>();
    ops.add(map);
    TaskId taskId = kato.requestOperations(ops).toBlocking().first();

    Map deployServerGroups =
        AsgDescriptionSupport.convertAsgsToDeploymentTargets(
            (List<Map<String, String>>) stage.getContext().get("asgs"));

    Map<String, Object> resultMap = new LinkedHashMap<>();
    resultMap.put("notification.type", "upserttencentcloudscheduledactions");
    resultMap.put("deploy.server.groups", deployServerGroups);
    resultMap.put("kato.last.task.id", taskId);
    TaskResult taskResult =
        TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(resultMap).build();

    return taskResult;
  }
}
