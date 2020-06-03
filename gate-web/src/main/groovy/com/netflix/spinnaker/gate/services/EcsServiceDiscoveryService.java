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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsServiceDiscoveryService {

  private ClouddriverService clouddriver;

  @Autowired
  public EcsServiceDiscoveryService(ClouddriverService clouddriver) {
    this.clouddriver = clouddriver;
  }

  public List<Map> getAllEcsServiceDiscoveryRegistries() {
    return clouddriver.getAllEcsServiceDiscoveryRegistries();
  }
}
