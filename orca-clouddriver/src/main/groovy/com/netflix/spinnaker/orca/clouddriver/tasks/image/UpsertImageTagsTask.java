/*
 * Copyright 2016 Netflix, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class UpsertImageTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private static final Logger log = LoggerFactory.getLogger(UpsertImageTagsTask.class);

  @Autowired
  KatoService kato;

  @Autowired
  List<ImageTagger> imageTaggers;

  @Value("${tasks.upsertImageTagsTimeoutMillis:600000}")
  private Long upsertImageTagsTimeoutMillis;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    ImageTagger tagger = imageTaggers.stream()
      .filter(it -> it.getCloudProvider().equals(cloudProvider))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("ImageTagger not found for cloudProvider " + cloudProvider));

    try {
      ImageTagger.OperationContext result = tagger.getOperationContext(stage);
      TaskId taskId = kato.requestOperations(cloudProvider, result.operations).toBlocking().first();

      return new TaskResult(ExecutionStatus.SUCCEEDED, ImmutableMap.<String, Object>builder()
        .put("notification.type", "upsertimagetags")
        .put("kato.last.task.id", taskId)
        .putAll(result.extraOutput)
        .build()
      );
    } catch (ImageTagger.ImageNotFound e) {
      if (e.shouldRetry) {
        log.error(String.format("Retrying... (reason: %s, executionId: %s, stageId: %s)", e.getMessage(), stage.getExecution().getId(), stage.getId()));
        return new TaskResult(ExecutionStatus.RUNNING);
      }

      throw e;
    }
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return upsertImageTagsTimeoutMillis;
  }
}
