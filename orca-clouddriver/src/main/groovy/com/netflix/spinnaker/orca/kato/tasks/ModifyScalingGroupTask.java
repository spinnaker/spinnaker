/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.orca.kato.tasks;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.kato.pipeline.support.ScalingGroupDescriptionSupport;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModifyScalingGroupTask implements Task {

  @Autowired KatoService kato;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    List list = new ArrayList<>();
    Map<String, Map> objectMap = new HashMap<>(16);
    Map<String, Object> context = stage.getContext();
    objectMap.put("modifyScalingGroupDescription", context);
    list.add(objectMap);
    Object taskId = kato.requestOperations(list).toBlocking().first();
    List<Map<String, String>> scalingGroups = (List) context.get("scalingGroups");
    Map deployServerGroups =
        ScalingGroupDescriptionSupport.convertScalingGroupToDeploymentTargets(scalingGroups);
    Map<String, Object> result =
        new ImmutableMap.Builder<String, Object>()
            .put("notification.type", "modifyScalingGroup")
            .put("deploy.server.groups", deployServerGroups)
            .put("kato.last.task.id", taskId)
            .build();
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(result).build();
  }
}
