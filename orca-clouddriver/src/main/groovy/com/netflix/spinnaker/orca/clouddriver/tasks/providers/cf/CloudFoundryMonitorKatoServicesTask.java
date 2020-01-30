/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static com.netflix.spinnaker.orca.ExecutionStatus.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.transform.CompileStatic;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CompileStatic
public class CloudFoundryMonitorKatoServicesTask extends AbstractCloudProviderAwareTask
    implements RetryableTask {
  private KatoService kato;

  @Autowired
  public CloudFoundryMonitorKatoServicesTask(KatoService kato) {
    this.kato = kato;
  }

  @Override
  public long getBackoffPeriod() {
    return 10 * 1000L;
  }

  @Override
  public long getTimeout() {
    return 30 * 60 * 1000L;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    TaskId taskId = stage.mapTo("/kato.last.task.id", TaskId.class);
    List<Map<String, Object>> katoTasks =
        Optional.ofNullable((List<Map<String, Object>>) stage.getContext().get("kato.tasks"))
            .orElse(new ArrayList<>());
    Map<String, Object> stageContext = stage.getContext();

    Task katoTask = kato.lookupTask(taskId.getId(), true);
    ExecutionStatus status = katoStatusToTaskStatus(katoTask);
    List<Map> results =
        Optional.ofNullable(katoTask.getResultObjects()).orElse(Collections.emptyList());

    ImmutableMap.Builder<String, Object> katoTaskMapBuilder =
        new ImmutableMap.Builder<String, Object>()
            .put("id", katoTask.getId())
            .put("status", katoTask.getStatus())
            .put("history", katoTask.getHistory())
            .put("resultObjects", results);

    ImmutableMap.Builder<String, Object> builder =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.last.task.id", taskId)
            .put("kato.task.firstNotFoundRetry", -1L)
            .put("kato.task.notFoundRetryCount", 0);

    switch (status) {
      case TERMINAL:
        {
          results.stream()
              .filter(result -> "EXCEPTION".equals(result.get("type")))
              .findAny()
              .ifPresent(e -> katoTaskMapBuilder.put("exception", e));
          break;
        }
      case SUCCEEDED:
        {
          builder
              .put("service.region", Optional.ofNullable(stageContext.get("region")).orElse(""))
              .put("service.account", getCredentials(stage))
              .put("service.operation.type", results.get(0).get("type"))
              .put("service.instance.name", results.get(0).get("serviceInstanceName"));
          break;
        }
      default:
    }

    katoTasks =
        katoTasks.stream()
            .filter(task -> !katoTask.getId().equals(task.get("id")))
            .collect(Collectors.toList());
    katoTasks.add(katoTaskMapBuilder.build());
    builder.put("kato.tasks", katoTasks);

    return TaskResult.builder(status).context(builder.build()).build();
  }

  private static ExecutionStatus katoStatusToTaskStatus(Task katoTask) {
    Task.Status katoStatus = katoTask.getStatus();
    if (katoStatus.isFailed()) {
      return TERMINAL;
    } else if (katoStatus.isCompleted()) {
      List<Map> results = katoTask.getResultObjects();
      if (results != null && results.size() > 0) {
        return SUCCEEDED;
      }
    }
    return RUNNING;
  }
}
