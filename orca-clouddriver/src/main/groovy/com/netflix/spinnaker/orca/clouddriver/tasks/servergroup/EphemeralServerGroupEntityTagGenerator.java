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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Component
public class EphemeralServerGroupEntityTagGenerator implements ServerGroupEntityTagGenerator {
  public static final String TTL_TAG = "spinnaker:ttl";

  @Override
  public Collection<Map<String, Object>> generateTags(Stage stage,
                                                      String serverGroup,
                                                      String account,
                                                      String location,
                                                      String cloudProvider) {
    StageData stageData = stage.mapTo(StageData.class);
    if (stageData.ttl.hours == null && stageData.ttl.minutes == null) {
      return Collections.emptyList();
    }

    ZonedDateTime expiry = ZonedDateTime.now(ZoneOffset.UTC);
    if (stageData.ttl.hours != null) {
      expiry = expiry.plus(stageData.ttl.hours, ChronoUnit.HOURS);
    }
    if (stageData.ttl.minutes != null) {
      expiry = expiry.plus(stageData.ttl.minutes, ChronoUnit.MINUTES);
    }

    Map<String, Object> value = ImmutableMap.<String, Object>builder()
      .put("serverGroup", serverGroup)
      .put("executionId", stage.getExecution().getId())
      .put("executionType", stage.getExecution().getType())
      .put("expiry", expiry)
      .build();

    return Collections.singletonList(
      ImmutableMap.<String, Object>builder()
        .put("name", TTL_TAG)
        .put("namespace", "spinnaker")
        .put("value", value)
        .build()
    );
  }

  private static class StageData {
    public TTL ttl;

    private static class TTL {
      public Integer hours;
      public Integer minutes;
    }
  }
}
