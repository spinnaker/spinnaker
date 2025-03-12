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

import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsCloudMetricProvider;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ecs/cloudMetrics")
public class EcsCloudMetricController {
  private final EcsCloudMetricProvider provider;

  @Autowired
  public EcsCloudMetricController(EcsCloudMetricProvider provider) {
    this.provider = provider;
  }

  @RequestMapping(value = {"/alarms"})
  public Collection<EcsMetricAlarm> findAllMetricAlarms() {
    return provider.getAllMetricAlarms();
  }
}
