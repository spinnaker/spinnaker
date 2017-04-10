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
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {
  @Autowired
  ArtifactService artifactService;

  @RequestMapping(value = "/publishBom", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> publishBom(
      @RequestParam String bomPath,
      @RequestBody String _ignored) {
    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> {
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
    StaticRequestBuilder<Void> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> {
      artifactService.writeArtifactConfig(bomPath, artifactName, profilePath);
      return null;
    });

    return DaemonTaskHandler.submitTask(builder::build, "Publish a " + artifactName + " profile");
  }
}
