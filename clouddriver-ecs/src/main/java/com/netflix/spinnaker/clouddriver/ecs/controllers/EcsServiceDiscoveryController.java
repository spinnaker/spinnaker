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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsServiceDiscoveryProvider;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EcsServiceDiscoveryController {

  EcsServiceDiscoveryProvider serviceDiscoveryProvider;

  @Autowired
  public EcsServiceDiscoveryController(EcsServiceDiscoveryProvider serviceDiscoveryProvider) {
    this.serviceDiscoveryProvider = serviceDiscoveryProvider;
  }

  @RequestMapping(value = {"/ecs/serviceDiscoveryRegistries"})
  public Collection<ServiceDiscoveryRegistry> getAllServiceDiscoveryRegistries() {
    return serviceDiscoveryProvider.getAllServiceDiscoveryRegistries();
  }
}
