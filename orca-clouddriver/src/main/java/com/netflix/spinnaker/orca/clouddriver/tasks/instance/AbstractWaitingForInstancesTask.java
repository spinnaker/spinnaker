/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractWaitingForInstancesTask extends AbstractInstancesCheckTask {
  private static final List<String> keys = List.of("disableAsg", "enableAsg", "enableServerGroup", "disableServerGroup");

  @Override
  protected Map<String, List<String>> getServerGroups(StageExecution stage) {
    return extractServerGroups(stage);
  }

  // "Desired Percentage" implies that some fraction of the instances in a server group have been enabled
  // or disabled -- likely during a rolling red/black operation.
  protected static int getDesiredInstanceCount(Map<String, Integer> capacity, Integer desiredPercentage) {
    if (desiredPercentage == null || desiredPercentage < 0 || desiredPercentage > 100) {
      throw new NumberFormatException("desiredPercentage must be an integer between 0 and 100");
    }

    //TODO: seems like this should be an error if it is null
    Integer desired = capacity.get("desired");

    Integer min = capacity.get("min") != null ? capacity.get("min") : desired;
    Integer max = capacity.get("max") != null ? capacity.get("max") : desired;

    // See https://docs.google.com/a/google.com/document/d/1rHe6JUkKGt58NaVO_3fHJt456Pw5kiX_sdVn1gwTZxk/edit?usp=sharing
    return (int) Math.ceil(((desiredPercentage / 100D) - 1D) * max + min);
  }

  static Map<String, List<String>> extractServerGroups(StageExecution stage) {
    Map<String, Object> context = stage.getContext();

    Map<String, List<String>> serverGroups = keys.stream()
        .map(k -> {
          String nameKey = String.format("targetop.asg.%s.name", k);

          // null signals to keep looking since the old code only checked keys until the first one was found
          Optional<Map<String, List<String>>> result = null;

          if (context.containsKey(nameKey)) {
            // has a key so stop looking
            String asg = (String) context.get(nameKey);
            List<String> regions = (List<String>) context.get(String.format("targetop.asg.%s.regions", k));
            if (asg == null || regions == null || regions.isEmpty()) {
              result = Optional.empty();
            } else {
              result =  Optional.of(regions.stream().collect(Collectors.toMap(Function.identity(), ignore ->  List.of(asg))));
            }
          }
          return result;
        })
        .filter(Objects::nonNull)
        .findFirst()
        .flatMap(Function.identity()) // unwrap the optional that was the stop looking signal
        .orElse((Map<String, List<String>>) context.get("deploy.server.groups"));

    return serverGroups;
  }
}
