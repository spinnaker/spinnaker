/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks.rollingpush;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CleanUpTagsTask implements CloudProviderAware, RetryableTask {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired KatoService katoService;

  @Autowired CloudDriverService cloudDriverService;

  @Autowired SourceResolver sourceResolver;

  @Autowired MonikerHelper monikerHelper;

  @Override
  public TaskResult execute(StageExecution stage) {
    try {
      StageData.Source source = sourceResolver.getSource(stage);
      String serverGroupName =
          Optional.ofNullable(source.getServerGroupName()).orElse(source.getAsgName());
      String cloudProvider = getCloudProvider(stage);

      ServerGroup serverGroup =
          cloudDriverService.getServerGroupFromCluster(
              monikerHelper.getAppNameFromStage(stage, serverGroupName),
              source.getAccount(),
              monikerHelper.getClusterNameFromStage(stage, serverGroupName),
              serverGroupName,
              source.getRegion(),
              cloudProvider);

      String imageId = (String) serverGroup.getLaunchConfig().get("imageId");

      List<EntityTags> tags =
          cloudDriverService.getEntityTags(
              cloudProvider,
              "servergroup",
              serverGroupName,
              source.getAccount(),
              source.getRegion());

      List<String> tagsToDelete =
          tags.stream()
              .flatMap(entityTag -> entityTag.tags.stream())
              .filter(tag -> "astrid_rules".equals(tag.namespace))
              .filter(hasNonMatchingImageId(imageId))
              .map(t -> t.name)
              .collect(Collectors.toList());

      log.info("found tags to delete {}", tagsToDelete);
      if (tagsToDelete.isEmpty()) {
        return TaskResult.SUCCEEDED;
      }

      // All IDs should be the same; use the first one
      String entityId = tags.get(0).id;

      TaskId taskId =
          katoService.requestOperations(cloudProvider, operations(entityId, tagsToDelete));

      return TaskResult.builder(SUCCEEDED)
          .context(
              new HashMap<String, Object>() {
                {
                  put("notification.type", "deleteentitytags");
                  put("kato.last.task.id", taskId);
                }
              })
          .build();
    } catch (Exception e) {
      log.error(
          "Failed to clean up tags for stage {} of {} {}",
          stage.getId(),
          stage.getExecution().getType(),
          stage.getExecution().getId(),
          e);
      return TaskResult.SUCCEEDED;
    }
  }

  private Predicate<EntityTags.Tag> hasNonMatchingImageId(String imageId) {
    return tag -> {
      if (EntityTags.ValueType.object != tag.valueType) {
        return false;
      }
      Map value = tag.value instanceof Map ? (Map) tag.value : Collections.emptyMap();
      return value.containsKey("imageId") && !value.get("imageId").equals(imageId);
    };
  }

  private List<Map<String, Map>> operations(String entityId, List<String> tags) {
    return Collections.singletonList(
        Collections.singletonMap(
            "deleteEntityTags",
            new HashMap<String, Object>() {
              {
                put("id", entityId);
                put("tags", tags);
              }
            }));
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
