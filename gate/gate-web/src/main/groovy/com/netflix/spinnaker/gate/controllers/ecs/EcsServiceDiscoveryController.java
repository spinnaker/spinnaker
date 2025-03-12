/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.gate.controllers.ecs;

import com.netflix.spinnaker.gate.services.EcsServiceDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EcsServiceDiscoveryController {
  @Autowired private EcsServiceDiscoveryService ecsServiceDiscoveryService;

  @Operation(
      summary =
          "Retrieve a list of Cloud Map services that can be used for the account and region.")
  @GetMapping(value = "/ecs/serviceDiscoveryRegistries")
  public List<Map> all() {
    return ecsServiceDiscoveryService.getAllEcsServiceDiscoveryRegistries();
  }
}
