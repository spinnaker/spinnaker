/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.service;

import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BuildServices {
  private final Map<String, BuildOperations> buildServices = new HashMap<>();

  public void addServices(Map<String, ? extends BuildOperations> services) {
    buildServices.putAll(services);
  }

  public BuildOperations getService(String name) {
    return buildServices.get(name);
  }

  public List<String> getServiceNames() {
    return buildServices.keySet().stream().sorted().collect(Collectors.toList());
  }

  public List<String> getServiceNames(BuildServiceProvider buildServiceProvider) {
    return buildServices.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .filter(e -> e.getValue().getBuildServiceProvider() == buildServiceProvider)
        .map(Map.Entry::getKey)
        .sorted()
        .collect(Collectors.toList());
  }

  public List<BuildService> getAllBuildServices() {
    return buildServices.values().stream()
        .map(BuildOperations::getView)
        .collect(Collectors.toList());
  }
}
