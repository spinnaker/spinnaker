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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/versions")
public class VersionsController {
  @Autowired
  VersionsService versionsService;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Versions> config() {
    DaemonResponse.StaticRequestBuilder<Versions> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(() -> versionsService.getVersions());
    return DaemonTaskHandler.submitTask(builder::build, "Get released versions");
  }

  @RequestMapping(value = "/latest/", method = RequestMethod.GET)
  DaemonTask<Halconfig, String> latest() {
    DaemonResponse.StaticRequestBuilder<String> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(() -> versionsService.getLatestSpinnakerVersion());
    return DaemonTaskHandler.submitTask(builder::build, "Get latest released version");
  }

  @RequestMapping(value = "/bom/{version:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, BillOfMaterials> bom(@PathVariable String version) {
    DaemonResponse.StaticRequestBuilder<BillOfMaterials> builder = new DaemonResponse.StaticRequestBuilder<>();
    builder.setBuildResponse(() -> versionsService.getBillOfMaterials(version));
    return DaemonTaskHandler.submitTask(builder::build, "Get BOM for " + version);
  }
}
