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
package com.netflix.spinnaker.front50.controllers;

import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import com.netflix.spinnaker.front50.model.plugins.PluginVersionPinningService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pluginVersions")
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class PluginVersionController {

  private final PluginVersionPinningService pluginVersionPinningService;

  public PluginVersionController(PluginVersionPinningService pluginVersionPinningService) {
    this.pluginVersionPinningService = pluginVersionPinningService;
  }

  @PutMapping("/{serverGroupName}")
  Map<String, PluginInfo.Release> pinVersions(
      @PathVariable String serverGroupName,
      @RequestParam String location,
      @RequestParam String serviceName,
      @RequestBody Map<String, String> pinnedVersions) {
    return pluginVersionPinningService.pinVersions(
        serviceName, location, serverGroupName, pinnedVersions);
  }
}
