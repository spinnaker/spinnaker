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

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.image.DeleteImageStage;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeleteImageTask implements CloudProviderAware, RetryableTask {
  private final KatoService katoService;

  @Autowired
  public DeleteImageTask(KatoService katoService) {
    this.katoService = katoService;
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    DeleteImageStage.DeleteImageRequest deleteImageRequest =
        stage.mapTo(DeleteImageStage.DeleteImageRequest.class);
    validateInputs(deleteImageRequest);

    List<Map<String, Map>> operations =
        deleteImageRequest.getImageIds().stream()
            .map(
                imageId -> {
                  Map<String, Object> operation = new HashMap<>();
                  operation.put("credentials", deleteImageRequest.getCredentials());
                  operation.put("region", deleteImageRequest.getRegion());
                  operation.put("imageId", imageId);

                  Map<String, Map> tmp = new HashMap<>();
                  tmp.put("deleteImage", operation);
                  return tmp;
                })
            .collect(toList());

    TaskId taskId =
        katoService.requestOperations(deleteImageRequest.getCloudProvider(), operations);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deleteImage");
    outputs.put("kato.last.task.id", taskId);
    outputs.put("delete.region", deleteImageRequest.getRegion());
    outputs.put("delete.account.name", deleteImageRequest.getCredentials());

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  private void validateInputs(DeleteImageStage.DeleteImageRequest createIssueRequest) {
    Set<ConstraintViolation<DeleteImageStage.DeleteImageRequest>> violations =
        Validation.buildDefaultValidatorFactory().getValidator().validate(createIssueRequest);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          "Failed validation: "
              + violations.stream()
                  .map(v -> String.format("%s: %s", v.getPropertyPath().toString(), v.getMessage()))
                  .collect(Collectors.toList()));
    }
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(10);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(2);
  }
}
