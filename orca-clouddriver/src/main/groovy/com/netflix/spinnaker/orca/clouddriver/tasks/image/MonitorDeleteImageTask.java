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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.pipeline.image.DeleteImageStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class MonitorDeleteImageTask extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {
  private static final Logger log = LoggerFactory.getLogger(MonitorDeleteImageTask.class);

  private final OortService oortService;

  @Autowired
  public MonitorDeleteImageTask(OortService oortService) {
    this.oortService = oortService;
  }

  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    DeleteImageStage.DeleteImageRequest deleteImageRequest = stage.mapTo(DeleteImageStage.DeleteImageRequest.class);
    List<String> deleteResult = (List<String>) Optional.ofNullable(stage.getOutputs().get("delete.image.ids"))
      .orElse(new ArrayList<>());

    Map<String, Object> outputs = new HashMap<>();
    deleteImageRequest
      .getImageIds()
      .forEach(imageId -> {
        try {
          oortService.getByAmiId(
            deleteImageRequest.getCloudProvider(),
            deleteImageRequest.getCredentials(),
            deleteImageRequest.getRegion(),
            imageId
          );
        } catch (RetrofitError e) {
          if (e.getResponse().getStatus() == 404) {
            deleteResult.add(imageId);
          } else {
            outputs.put("lastRetrofitException", new RetrofitExceptionHandler().handle(stage.getName(), e));
            log.error("Unexpected retrofit error {}", outputs.get("lastRetrofitException"), e);
          }
        }
      });

    outputs.put("delete.image.ids", deleteResult);
    if (deleteResult.containsAll(deleteImageRequest.getImageIds())) {
      return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
    }

    return new TaskResult(ExecutionStatus.RUNNING, outputs);
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
