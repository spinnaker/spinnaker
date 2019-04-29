/*
 *
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.igor.artifacts;

import com.netflix.spinnaker.igor.model.ArtifactServiceProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactServices {
  private final Map<String, ArtifactService> artifactServices = new HashMap<>();

  public void addServices(Map<String, ? extends ArtifactService> services) {
    artifactServices.putAll(services);
  }

  public ArtifactService getService(String name) {
    return artifactServices.get(name);
  }

  public List<String> getServiceNames() {
    return artifactServices.keySet().stream().sorted().collect(Collectors.toList());
  }

  public List<String> getServiceNames(ArtifactServiceProvider artifactServiceProvider) {
    return artifactServices.entrySet().stream()
        .filter(
            e ->
                e.getValue() != null
                    && e.getValue().artifactServiceProvider() == artifactServiceProvider)
        .map(Map.Entry::getKey)
        .sorted()
        .collect(Collectors.toList());
  }
}
