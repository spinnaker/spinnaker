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
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper functions that are used in tasks that check on the status of instances. These were
 * previously exposed via a class hierarchy but are pure functions.
 */
public class WaitingForInstancesTaskHelper {
  private static final List<String> keys =
      List.of("disableAsg", "enableAsg", "enableServerGroup", "disableServerGroup");

  /**
   * Calculate the number of instances that are required to meet the desired instance count for a
   * task to complete. See
   * https://docs.google.com/a/google.com/document/d/1rHe6JUkKGt58NaVO_3fHJt456Pw5kiX_sdVn1gwTZxk/edit?usp=sharing
   *
   * @param capacity TODO: use actual Capacity class
   * @param desiredPercentage implies that some fraction of the instances in a server group have
   *     been enabled or disabled -- likely during a rolling red/black operation.
   * @return
   */
  public static int getDesiredInstanceCount(
      ServerGroup.Capacity capacity, Integer desiredPercentage) {
    if (desiredPercentage == null || desiredPercentage < 0 || desiredPercentage > 100) {
      throw new NumberFormatException("desiredPercentage must be an integer between 0 and 100");
    }

    // TODO: seems like this should be an error if it is null
    Integer desired = capacity.getDesired();

    Integer min = capacity.getMin() != null ? capacity.getMin() : desired;
    Integer max = capacity.getMax() != null ? capacity.getMax() : desired;

    return (int) Math.ceil(((desiredPercentage / 100D) - 1D) * max + min);
  }

  /**
   * Extracts the Server Groups from the stage context that are being monitored for status.
   *
   * @param stage
   * @return A Map of Region to List of Server Groups
   */
  public static Map<String, List<String>> extractServerGroups(StageExecution stage) {
    Map<String, Object> context = stage.getContext();

    Map<String, List<String>> serverGroups =
        keys.stream()
            .map(
                k -> {
                  String nameKey = String.format("targetop.asg.%s.name", k);

                  // null signals to keep looking since the old code only checked keys until the
                  // first one was found
                  Optional<Map<String, List<String>>> result = null;

                  if (context.containsKey(nameKey)) {
                    // has a key so stop looking
                    String asg = (String) context.get(nameKey);
                    List<String> regions =
                        (List<String>) context.get(String.format("targetop.asg.%s.regions", k));
                    if (asg == null || regions == null || regions.isEmpty()) {
                      result = Optional.empty();
                    } else {
                      result =
                          Optional.of(
                              regions.stream()
                                  .collect(
                                      Collectors.toMap(
                                          Function.identity(), ignore -> List.of(asg))));
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
