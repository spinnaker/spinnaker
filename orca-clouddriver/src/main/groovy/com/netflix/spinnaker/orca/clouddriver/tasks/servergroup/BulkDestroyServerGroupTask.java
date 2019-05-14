/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage.getPIPELINE_CONFIG_TYPE;

import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BulkDestroyServerGroupTask extends AbstractBulkServerGroupTask
    implements RetryableTask {
  @Autowired private TrafficGuard trafficGuard;

  @Override
  String getClouddriverOperation() {
    return getPIPELINE_CONFIG_TYPE();
  }

  @Override
  void validateClusterStatus(Map<String, Object> operation, Moniker moniker) {
    trafficGuard.verifyTrafficRemoval(
        (String) operation.get("serverGroupName"),
        moniker,
        getCredentials(operation),
        getLocation(operation),
        getCloudProvider(operation),
        "Destroying");
  }
}
