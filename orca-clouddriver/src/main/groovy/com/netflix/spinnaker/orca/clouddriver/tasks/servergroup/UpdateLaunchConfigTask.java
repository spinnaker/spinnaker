/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import groovy.util.logging.Slf4j;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateLaunchConfigTask extends AbstractUpdateLaunchSettingsTask {
  public static final String OPERATION = "updateLaunchConfig";

  @Autowired
  public UpdateLaunchConfigTask(
      KatoService kato, @Value("${default.bake.account:default}") String defaultBakeAccount) {
    super(kato, defaultBakeAccount);
  }

  @Override
  public Map<String, Object> getContext(StageExecution stage, TaskId taskId) {
    final Map<String, Object> ctx = new HashMap<>();
    final String region = (String) stage.getContext().get("region");
    final String serverGroupName =
        (String)
            stage
                .getContext()
                .getOrDefault(
                    stage.getContext().get("serverGroupName"), stage.getContext().get("asgName"));

    ctx.put("notification.type", "modifyasglaunchconfiguration");
    ctx.put("modifyasglaunchconfiguration.account.name", getCredentials(stage));
    ctx.put("modifyasglaunchconfiguration.region", region);
    ctx.put("kato.last.task.id", taskId);
    ctx.put(
        "deploy.server.groups",
        Collections.singletonMap(region, Collections.singletonList(serverGroupName)));
    return ctx;
  }

  @Override
  public String getOperation() {
    return OPERATION;
  }
}
