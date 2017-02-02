/*
 * Copyright 2016 Google, Inc.
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
import com.netflix.spinnaker.halyard.config.services.v1.ConfigService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports the entire contents of ~/.hal/config
 */
@RestController
@RequestMapping("/v1/config")
public class ConfigController {
  @Autowired
  ConfigService configService;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonResponse<Halconfig> config() {
    StaticRequestBuilder<Halconfig> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> configService.getConfig());
    return builder.build();
  }

  @RequestMapping(value = "/currentDeployment", method = RequestMethod.GET)
  DaemonResponse<String> currentDeployment() {
    StaticRequestBuilder<String> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> configService.getCurrentDeployment());
    return builder.build();
  }
}
