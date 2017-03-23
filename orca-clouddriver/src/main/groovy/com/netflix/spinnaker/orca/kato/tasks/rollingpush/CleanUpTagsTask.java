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

import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.DefaultTaskResult;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CleanUpTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  @Autowired
  KatoService katoService;

  @Autowired
  OortService oortService;

  @Autowired
  SourceResolver sourceResolver;

  @Override
  public TaskResult execute(Stage stage) {
    try {
      StageData.Source source = sourceResolver.getSource(stage);
      String serverGroupName =  source.getServerGroupName() != null ? source.getServerGroupName() : source.getAsgName();
      String imageId = (String) stage.getContext().getOrDefault("imageId", stage.getContext().get("amiName"));

      String cloudProvider = getCloudProvider(stage);

      List<Map> tags = oortService.getEntityTags(
        cloudProvider,
        "servergroup",
        serverGroupName,
        source.getAccount(),
        source.getRegion()
      );

      List<String> tagsToDelete = new ArrayList<>();
      tags.forEach( s -> tagsToDelete.addAll(
        ((List<Map>) s.get("tags"))
          .stream()
          .filter(hasNonMatchingImageId(imageId))
          .map(t -> (String) t.get("name"))
          .collect(Collectors.toList())
      ));

      log.info("found tags to delete {}", tagsToDelete);
      if (tagsToDelete.isEmpty()) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED);
      }

      TaskId taskId = katoService.requestOperations(
        cloudProvider,
        operations(serverGroupName, tagsToDelete)
      ).toBlocking().first();

      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, new HashMap<String, Object>() {{
        put("notification.type", "deleteentitytags");
        put("kato.last.task.id", taskId);
      }});

    } catch (Exception e) {
      log.error("Failed to clean up tags for stage {} ",stage, e);
      return new DefaultTaskResult(ExecutionStatus.FAILED_CONTINUE);
    }

  }

  private Predicate<Map> hasNonMatchingImageId(String imageId) {
    return tag -> {
      Map value = ((Map) tag.getOrDefault("value", Collections.EMPTY_MAP));
      return value.containsKey("imageId") && !value.get("imageId").equals(imageId);
    };
  }

  private List<Map<String, Map>> operations(String serverGroupName, List<String> tags) {
    return Collections.singletonList(Collections.singletonMap("deleteEntityTags", new HashMap<String, Object>() {
      {
        put("id", serverGroupName);
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
