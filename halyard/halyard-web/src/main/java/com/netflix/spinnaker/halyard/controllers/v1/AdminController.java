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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminController {
  private final ArtifactService artifactService;

  @RequestMapping(value = "/publishLatest", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> publishLatest(
      @RequestParam(required = false) String latestSpinnaker,
      @RequestParam(required = false) String latestHalyard,
      @RequestBody String _ignored) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              if (!StringUtils.isEmpty(latestSpinnaker)) {
                artifactService.publishLatestSpinnaker(latestSpinnaker);
              }

              if (!StringUtils.isEmpty(latestHalyard)) {
                artifactService.publishLatestHalyard(latestHalyard);
              }
              return null;
            });

    return DaemonTaskHandler.submitTask(builder::build, "Update the latest version");
  }

  @RequestMapping(value = "/deprecateVersion", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> deprecateVersion(
      @RequestParam(required = false) String illegalReason, @RequestBody Versions.Version version) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              artifactService.deprecateVersion(version, illegalReason);
              return null;
            });

    return DaemonTaskHandler.submitTask(
        builder::build, "Deprecate version " + version.getVersion());
  }

  @RequestMapping(value = "/publishVersion", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> publishVersion(@RequestBody Versions.Version version) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              artifactService.publishVersion(version);
              return null;
            });

    return DaemonTaskHandler.submitTask(
        builder::build, "Publish a new version " + version.getVersion());
  }

  @RequestMapping(value = "/publishBom", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> publishBom(
      @RequestParam String bomPath, @RequestBody String _ignored) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              artifactService.writeBom(bomPath);
              return null;
            });

    return DaemonTaskHandler.submitTask(builder::build, "Publish a BOM");
  }

  @RequestMapping(value = "/publishProfile/{artifactName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> publishProfile(
      @RequestParam String bomPath,
      @PathVariable String artifactName,
      @RequestParam String profilePath,
      @RequestBody String _ignored) {
    StaticRequestBuilder<Void> builder =
        new StaticRequestBuilder<>(
            () -> {
              artifactService.writeArtifactConfig(bomPath, artifactName, profilePath);
              return null;
            });

    return DaemonTaskHandler.submitTask(builder::build, "Publish a " + artifactName + " profile");
  }
}
