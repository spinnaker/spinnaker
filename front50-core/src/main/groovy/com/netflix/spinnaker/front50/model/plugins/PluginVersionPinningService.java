/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.front50.model.plugins;

import static java.lang.String.format;

import java.util.HashMap;
import java.util.Map;

public class PluginVersionPinningService {

  private final PluginVersionPinningRepository pluginVersionPinningRepository;
  private final PluginInfoRepository pluginInfoRepository;

  public PluginVersionPinningService(
      PluginVersionPinningRepository pluginVersionPinningRepository,
      PluginInfoRepository pluginInfoRepository) {
    this.pluginVersionPinningRepository = pluginVersionPinningRepository;
    this.pluginInfoRepository = pluginInfoRepository;
  }

  public Map<String, PluginInfo.Release> pinVersions(
      String serviceName,
      String location,
      String serverGroupName,
      Map<String, String> pluginVersions) {
    String id = format("%s-%s-%s", serviceName, location, serverGroupName);

    ServerGroupPluginVersions existing = pluginVersionPinningRepository.findById(id);
    if (existing == null) {
      pluginVersionPinningRepository.create(
          id, new ServerGroupPluginVersions(id, serverGroupName, location, pluginVersions));
      return getReleasesForIds(pluginVersions);
    }
    return getReleasesForIds(existing.pluginVersions);
  }

  private Map<String, PluginInfo.Release> getReleasesForIds(Map<String, String> versions) {
    Map<String, PluginInfo.Release> releases = new HashMap<>();

    versions.forEach(
        (pluginId, version) ->
            pluginInfoRepository
                .findById(pluginId)
                .getReleaseByVersion(version)
                .ifPresent(it -> releases.put(pluginId, it)));

    return releases;
  }
}
