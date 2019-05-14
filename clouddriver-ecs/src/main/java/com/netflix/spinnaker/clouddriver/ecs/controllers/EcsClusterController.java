/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsClusterProvider;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EcsClusterController {

  EcsClusterProvider ecsClusterProvider;

  @Autowired
  public EcsClusterController(EcsClusterProvider ecsClusterProvider) {
    this.ecsClusterProvider = ecsClusterProvider;
  }

  @RequestMapping(value = {"/ecs/ecsClusters"})
  public Collection<EcsCluster> getAllEcsClusters() {
    return ecsClusterProvider.getAllEcsClusters();
  }
}
