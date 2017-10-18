/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.frigga.Names;
import com.netflix.spinnaker.orca.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class SpinnakerMetadataServerGroupTagGenerator implements ServerGroupEntityTagGenerator {
  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private final OortService oortService;
  private final RetrySupport retrySupport;

  public SpinnakerMetadataServerGroupTagGenerator(OortService oortService, RetrySupport retrySupport) {
    this.oortService = oortService;
    this.retrySupport = retrySupport;
  }

  @Override
  public Collection<Map<String, Object>> generateTags(Stage stage, String serverGroup, String account, String location, String cloudProvider) {
    Execution execution = stage.getExecution();
    Map context = stage.getContext();

    Map<String, Object> value = new HashMap<>();
    value.put("stageId", stage.getId());
    value.put("executionId", execution.getId());
    value.put("executionType", execution.getClass().getSimpleName().toLowerCase());
    value.put("application", execution.getApplication());

    if (execution.getAuthentication() != null) {
      value.put("user", execution.getAuthentication().getUser());
    }

    if (execution instanceof Orchestration) {
      value.put("description", ((Orchestration) execution).getDescription());
    } else if (execution instanceof Pipeline) {
      value.put("description", execution.getName());
      value.put("pipelineConfigId", ((Pipeline) execution).getPipelineConfigId());
    }

    if (context.containsKey("reason") && context.get("reason") != null) {
      value.put("comments", (String) context.get("reason"));
    }
    if (context.containsKey("comments") && context.get("comments") != null) {
      value.put("comments", (String) context.get("comments"));
    }

    String cluster = null;
    try {
      cluster = Names.parseName(serverGroup).getCluster();

      Map<String, Object> previousServerGroup = getPreviousServerGroup(
        execution.getApplication(),
        account,
        cluster,
        cloudProvider,
        location
      );

      if (previousServerGroup != null) {
        value.put("previousServerGroup", previousServerGroup);
      }
    } catch (Exception e) {
      // failure to populate `previousServerGroup` is not considered a fatal error that would cause this task to fail
      log.error("Unable to determine ancestor image details for {}:{}:{}:{}", cloudProvider, account, location, cluster, e);
    }

    Map<String, Object> tag = new HashMap<>();
    tag.put("name", "spinnaker:metadata");
    tag.put("value", value);

    return Collections.singletonList(tag);
  }

  Map<String, Object> getPreviousServerGroup(String application,
                                             String account,
                                             String cluster,
                                             String cloudProvider,
                                             String location) {
    if (cloudProvider.equals("titus")) {
      // TODO-AJ titus does not force cache refresh so `ANCESTOR` will return inconsistent results
      return null;
    }

    return retrySupport.retry(() -> {
      try {
        Map<String, Object> targetServerGroup = oortService.getServerGroupSummary(
          application,
          account,
          cluster,
          cloudProvider,
          location,
          "ANCESTOR",
          "image",
          "true"
        );

        Map<String, Object> previousServerGroup = new HashMap<>();
        previousServerGroup.put("name", targetServerGroup.get("serverGroupName"));
        previousServerGroup.put("imageName", targetServerGroup.get("imageName"));
        previousServerGroup.put("imageId", targetServerGroup.get("imageId"));
        previousServerGroup.put("cloudProvider", cloudProvider);

        return previousServerGroup;
      } catch (RetrofitError e) {
        if (e.getKind() == RetrofitError.Kind.HTTP && e.getResponse().getStatus() == 404) {
          // it's ok if the previous server group does not exist
          return null;
        }

        throw e;
      }
    }, 12, 5000, false); // retry for up to one minute
  }
}
