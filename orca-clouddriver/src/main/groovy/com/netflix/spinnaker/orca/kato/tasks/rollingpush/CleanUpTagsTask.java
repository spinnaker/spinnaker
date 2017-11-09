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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;

@Component
public class CleanUpTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  KatoService katoService;

  @Autowired
  OortService oortService;

  @Autowired
  SourceResolver sourceResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonikerHelper monikerHelper;

  @Override
  public TaskResult execute(Stage stage) {
    try {
      StageData.Source source = sourceResolver.getSource(stage);
      String serverGroupName = Optional.ofNullable(source.getServerGroupName()).orElse(source.getAsgName());
      String cloudProvider = getCloudProvider(stage);

      Response serverGroupResponse = oortService.getServerGroupFromCluster(
        monikerHelper.getAppNameFromStage(stage, serverGroupName),
        source.getAccount(),
        monikerHelper.getClusterNameFromStage(stage, serverGroupName),
        serverGroupName,
        source.getRegion(),
        cloudProvider
      );

      Map serverGroup = objectMapper.readValue(serverGroupResponse.getBody().in(), Map.class);
      String imageId = (String) ((Map) serverGroup.get("launchConfig")).get("imageId");

      List<Map> tags = oortService.getEntityTags(
        cloudProvider,
        "servergroup",
        serverGroupName,
        source.getAccount(),
        source.getRegion()
      );

      List<String> tagsToDelete = tags.stream()
        .flatMap(entityTag -> ((List<Map>) entityTag.get("tags")).stream())
        .filter(tag -> "astrid_rules".equals(tag.get("namespace")))
        .filter(hasNonMatchingImageId(imageId))
        .map(t -> (String) t.get("name"))
        .collect(Collectors.toList());

      log.info("found tags to delete {}", tagsToDelete);
      if (tagsToDelete.isEmpty()) {
        return new TaskResult(SUCCEEDED);
      }

      // All IDs should be the same; use the first one
      String entityId = (String) tags.get(0).get("id");

      TaskId taskId = katoService.requestOperations(
        cloudProvider,
        operations(entityId, tagsToDelete)
      ).toBlocking().first();

      return new TaskResult(SUCCEEDED, new HashMap<String, Object>() {{
        put("notification.type", "deleteentitytags");
        put("kato.last.task.id", taskId);
      }});
    } catch (Exception e) {
      log.error(
        "Failed to clean up tags for stage {} of {} {}",
        stage.getId(),
        stage.getExecution().getType(),
        stage.getExecution().getId(),
        e
      );
      return new TaskResult(SUCCEEDED);
    }
  }

  private Predicate<Map> hasNonMatchingImageId(String imageId) {
    return tag -> {
      if (!"object".equals(tag.get("valueType"))) {
        return false;
      }
      Map value = ((Map) tag.getOrDefault("value", Collections.EMPTY_MAP));
      return value.containsKey("imageId") && !value.get("imageId").equals(imageId);
    };
  }

  private List<Map<String, Map>> operations(String entityId, List<String> tags) {
    return Collections.singletonList(Collections.singletonMap("deleteEntityTags", new HashMap<String, Object>() {
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
