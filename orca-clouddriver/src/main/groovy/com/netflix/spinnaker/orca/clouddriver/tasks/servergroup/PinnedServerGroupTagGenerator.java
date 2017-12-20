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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Component
public class PinnedServerGroupTagGenerator implements ServerGroupEntityTagGenerator {
  public static final String PINNED_CAPACITY_TAG = "spinnaker:pinned_capacity";

  @Override
  public Collection<Map<String, Object>> generateTags(Stage stage,
                                                      String serverGroup,
                                                      String account,
                                                      String location,
                                                      String cloudProvider) {
    StageData stageData = stage.mapTo(StageData.class);
    if (stageData.capacity == null || stageData.sourceServerGroupCapacitySnapshot == null) {
      return Collections.emptyList();
    }

    if (stageData.capacity.min.equals(stageData.sourceServerGroupCapacitySnapshot.min)) {
      // min capacity was not actually pinned, no need to mark this server group as having a pinned capacity
      return Collections.emptyList();
    }

    Map<String, Object> value = ImmutableMap.<String, Object>builder()
      .put("serverGroup", serverGroup)
      .put("account", account)
      .put("location", location)
      .put("cloudProvider", cloudProvider)
      .put("executionId", stage.getExecution().getId())
      .put("executionType", stage.getExecution().getType())
      .put("stageId", stage.getId())
      .put("pinnedCapacity", stageData.capacity.toMap())
      .put("unpinnedCapacity", stageData.sourceServerGroupCapacitySnapshot.toMap())
      .build();

    return Collections.singletonList(
      ImmutableMap.<String, Object>builder()
        .put("name", PINNED_CAPACITY_TAG)
        .put("value", value)
        .build()
    );
  }

  private static class StageData {
    public Capacity capacity;
    public Capacity sourceServerGroupCapacitySnapshot;

    private static class Capacity {
      public Integer min;
      public Integer desired;
      public Integer max;

      Map<String, Integer> toMap() {
        return ImmutableMap.<String, Integer>builder()
          .put("min", min)
          .put("desired", desired)
          .put("max", max)
          .build();
      }
    }
  }
}
