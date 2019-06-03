/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1;

import java.util.*;
import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Data
public class RunningServiceDetails {
  LoadBalancer loadBalancer;
  String artifactId;
  String internalEndpoint;
  String externalEndpoint;
  // Map of server group version (N) -> instance set
  Map<Integer, List<Instance>> instances = new HashMap<>();

  public Integer getLatestEnabledVersion() {
    List<Integer> versions = new ArrayList<>(instances.keySet());
    if (!versions.isEmpty()) {
      versions.sort(Integer::compareTo);
      versions.sort(Comparator.reverseOrder());
    }

    return versions.stream()
        .map(i -> new ImmutablePair<>(i, instances.get(i)))
        .filter(is -> is.getRight().stream().allMatch(i -> i.isHealthy() && i.isRunning()))
        .findFirst()
        .orElse(new ImmutablePair<>(null, null))
        .getLeft();
  }

  @Data
  public static class Instance {
    String id;
    String location;
    boolean running;
    boolean healthy;
  }

  @Data
  public static class LoadBalancer {
    boolean exists;
  }
}
