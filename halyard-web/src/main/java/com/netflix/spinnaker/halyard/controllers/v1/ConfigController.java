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

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.model.v1.Halconfig;
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
  HalconfigParser halyardConfig;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  Halconfig config() throws UnrecognizedPropertyException {
    return halyardConfig.getConfig();
  }

  @RequestMapping(value = "/currentDeployment", method = RequestMethod.GET)
  String currentDeployment() throws UnrecognizedPropertyException {
    Halconfig halconfig = halyardConfig.getConfig();
    if (halconfig != null) {
      return halconfig.getCurrentDeployment();
    } else {
      return null;
    }
  }
}
