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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/versions")
public class VersionsController {
  private final VersionsService versionsService;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Versions> config() {
    DaemonResponse.StaticRequestBuilder<Versions> builder =
        new DaemonResponse.StaticRequestBuilder<>(versionsService::getVersions);
    return DaemonTaskHandler.submitTask(builder::build, "Get released versions");
  }

  @RequestMapping(value = "/latest/", method = RequestMethod.GET)
  DaemonTask<Halconfig, String> latest() {
    DaemonResponse.StaticRequestBuilder<String> builder =
        new DaemonResponse.StaticRequestBuilder<>(versionsService::getLatestSpinnakerVersion);
    return DaemonTaskHandler.submitTask(builder::build, "Get latest released version");
  }

  /**
   * This method is deprecated because of the inability to handle encoded forward slashes in the
   * path variable. Although it is possible to configure Spring to allow this, it is recommended
   * that the variable be moved to a request variable:
   *
   * <p>https://stackoverflow.com/questions/13482020/encoded-slash-2f-with-spring-requestmapping-path-param-gives-http-400
   *
   * <p>Please use bomV2 instead.
   */
  @Deprecated
  @RequestMapping(value = "/bom/{version:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, BillOfMaterials> bom(@PathVariable String version) {
    return bomV2(version);
  }

  @RequestMapping(value = "/bom", method = RequestMethod.GET)
  DaemonTask<Halconfig, BillOfMaterials> bomV2(@RequestParam(value = "version") String version) {
    DaemonResponse.StaticRequestBuilder<BillOfMaterials> builder =
        new DaemonResponse.StaticRequestBuilder<>(
            () -> versionsService.getBillOfMaterials(version));
    return DaemonTaskHandler.submitTask(builder::build, "Get BOM for " + version);
  }
}
