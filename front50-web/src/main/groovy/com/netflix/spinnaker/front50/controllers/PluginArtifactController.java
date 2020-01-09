/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spinnaker.front50.model.pluginartifact.PluginArtifact;
import com.netflix.spinnaker.front50.model.pluginartifact.PluginArtifactService;
import java.util.Collection;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** TODO(rz): What's the permissions model for something like artifacts? */
@RestController
@RequestMapping("/pluginArtifacts")
public class PluginArtifactController {

  private final PluginArtifactService pluginArtifactService;

  public PluginArtifactController(PluginArtifactService pluginArtifactService) {
    this.pluginArtifactService = pluginArtifactService;
  }

  @RequestMapping(value = "", method = RequestMethod.GET)
  Collection<PluginArtifact> list(
      @RequestParam(value = "service", required = false) String service) {
    return Optional.ofNullable(service)
        .map(pluginArtifactService::findAllByService)
        .orElseGet(pluginArtifactService::findAll);
  }

  @RequestMapping(value = "", method = RequestMethod.POST)
  PluginArtifact upsert(@RequestBody PluginArtifact pluginArtifact) {
    return pluginArtifactService.upsert(pluginArtifact);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String id) {
    pluginArtifactService.delete(id);
  }

  @RequestMapping(value = "/{id}/releases", method = RequestMethod.POST)
  PluginArtifact createRelease(
      @PathVariable String id, @RequestBody PluginArtifact.Release release) {
    return pluginArtifactService.createRelease(id, release);
  }

  @RequestMapping(value = "/{id}/releases/{releaseVersion}", method = RequestMethod.DELETE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  PluginArtifact deleteRelease(@PathVariable String id, @PathVariable String releaseVersion) {
    return pluginArtifactService.deleteRelease(id, releaseVersion);
  }
}
