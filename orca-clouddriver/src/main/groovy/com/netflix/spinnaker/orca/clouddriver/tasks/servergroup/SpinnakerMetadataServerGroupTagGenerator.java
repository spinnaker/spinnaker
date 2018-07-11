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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;

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
  public Collection<Map<String, Object>> generateTags(Stage stage,
                                                      String serverGroup,
                                                      String account,
                                                      String location,
                                                      String cloudProvider) {
    Execution execution = stage.getExecution();
    Map context = stage.getContext();

    Map<String, Object> value = new HashMap<>();
    value.put("stageId", stage.getId());
    value.put("executionId", execution.getId());
    value.put("executionType", execution.getType().toString());
    value.put("application", execution.getApplication());

    if (execution.getAuthentication() != null) {
      value.put("user", execution.getAuthentication().getUser());
    }

    if (execution.getType() == ORCHESTRATION) {
      value.put("description", execution.getDescription());
    } else if (execution.getType() == PIPELINE) {
      value.put("description", execution.getName());
      value.put("pipelineConfigId", execution.getPipelineConfigId());
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

      Map<String, Object> previousServerGroup = getPreviousServerGroupFromCluster(
        execution.getApplication(),
        account,
        cluster,
        cloudProvider,
        location,
        serverGroup
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
    tag.put("namespace", "spinnaker");
    tag.put("value", value);

    return Collections.singletonList(tag);
  }

  Map<String, Object> getPreviousServerGroupFromCluster(String application,
                                                        String account,
                                                        String cluster,
                                                        String cloudProvider,
                                                        String location,
                                                        String createdServerGroup) {
    if (cloudProvider.equals("titus")) {
      // titus does not (currently!) force cache refresh to it's possible that `NEWEST` is actually the `ANCESTOR` to
      // the just created server group
      Map<String, Object> newestServerGroup = retrySupport.retry(() -> {
        return getPreviousServerGroupFromClusterByTarget(
          application, account, cluster, cloudProvider, location, "NEWEST"
        );
      }, 10, 3000, false); // retry for up to 30 seconds

      if (newestServerGroup == null) {
        // cluster has no enabled server groups
        return null;
      }

      if (!newestServerGroup.get("name").equals(createdServerGroup)) {
        // if the `NEWEST` server group is _NOT_ what was just created, we've found our `ANCESTOR`
        // if not, fall through to an explicit `ANCESTOR` lookup
        return newestServerGroup;
      }
    }

    return retrySupport.retry(() -> {
      return getPreviousServerGroupFromClusterByTarget(
        application, account, cluster, cloudProvider, location, "ANCESTOR"
      );
    }, 10, 3000, false); // retry for up to 30 seconds
  }

  Map<String, Object> getPreviousServerGroupFromClusterByTarget(String application,
                                                                String account,
                                                                String cluster,
                                                                String cloudProvider,
                                                                String location,
                                                                String target) {
    try {
      Map<String, Object> targetServerGroup = oortService.getServerGroupSummary(
        application,
        account,
        cluster,
        cloudProvider,
        location,
        target,
        "image",
        "true"
      );

      Map<String, Object> previousServerGroup = new HashMap<>();
      previousServerGroup.put("name", targetServerGroup.get("serverGroupName"));
      previousServerGroup.put("imageName", targetServerGroup.get("imageName"));
      previousServerGroup.put("imageId", targetServerGroup.get("imageId"));
      previousServerGroup.put("cloudProvider", cloudProvider);

      if (targetServerGroup.containsKey("buildInfo")) {
        previousServerGroup.put("buildInfo", targetServerGroup.get("buildInfo"));
      }

      return previousServerGroup;
    } catch (RetrofitError e) {
      if (e.getKind() == RetrofitError.Kind.HTTP && e.getResponse().getStatus() == 404) {
        // it's ok if the previous server group does not exist
        return null;
      }

      throw e;
    }
  }
}
