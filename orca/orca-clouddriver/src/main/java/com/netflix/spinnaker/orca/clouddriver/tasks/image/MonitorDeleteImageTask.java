/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.image;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.image.DeleteImageStage;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitorDeleteImageTask implements CloudProviderAware, OverridableTimeoutRetryableTask {
  private static final Logger log = LoggerFactory.getLogger(MonitorDeleteImageTask.class);

  private final CloudDriverService cloudDriverService;

  @Autowired
  public MonitorDeleteImageTask(CloudDriverService cloudDriverService) {
    this.cloudDriverService = cloudDriverService;
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    DeleteImageStage.DeleteImageRequest deleteImageRequest =
        stage.mapTo(DeleteImageStage.DeleteImageRequest.class);
    List<String> deleteResult =
        (List<String>)
            Optional.ofNullable(stage.getOutputs().get("delete.image.ids"))
                .orElse(new ArrayList<>());

    Map<String, Object> outputs = new HashMap<>();
    deleteImageRequest
        .getImageIds()
        .forEach(
            imageId -> {
              try {
                cloudDriverService.getByAmiId(
                    deleteImageRequest.getCloudProvider(),
                    deleteImageRequest.getCredentials(),
                    deleteImageRequest.getRegion(),
                    imageId);
              } catch (SpinnakerHttpException e) {
                if (e.getResponseCode() == 404) {
                  deleteResult.add(imageId);
                } else {
                  outputs.put(
                      "lastSpinnakerException",
                      new SpinnakerServerExceptionHandler().handle(stage.getName(), e));
                  log.error("Unexpected http error {}", outputs.get("lastSpinnakerException"), e);
                }
              }
            });

    outputs.put("delete.image.ids", deleteResult);
    if (deleteResult.containsAll(deleteImageRequest.getImageIds())) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build();
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }
}
