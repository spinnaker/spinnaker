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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/capabilities")
@RequiredArgsConstructor
public class CapabilitiesController {

  private final OrcaServiceSelector orcaService;

  @ApiOperation(value = "Retrieve the list configured deployment monitors", response = List.class)
  @GetMapping(value = "/deploymentMonitors")
  List<Object> getDeploymentMonitors() {
    return orcaService.select().getDeploymentMonitors();
  }

  @ApiOperation(
      value = "Retrieve the SpEL expression capabilities (e.g. registered functions, etc)",
      response = Map.class)
  @GetMapping(value = "/expressions")
  Map getExpressionCapabilities() {
    return orcaService.select().getExpressionCapabilities();
  }
}
