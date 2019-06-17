/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleComputeApiFactory {

  private final GoogleOperationPoller operationPoller;
  private final Registry registry;

  @Autowired
  public GoogleComputeApiFactory(GoogleOperationPoller operationPoller, Registry registry) {
    this.operationPoller = operationPoller;
    this.registry = registry;
  }

  public GoogleServerGroupManagers createServerGroupManagers(
      GoogleNamedAccountCredentials credentials, GoogleServerGroup.View serverGroup) {
    return serverGroup.getRegional()
        ? new RegionGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getRegion())
        : new ZoneGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getZone());
  }

  public InstanceTemplates createInstanceTemplates(GoogleNamedAccountCredentials credentials) {
    return new InstanceTemplates(credentials, operationPoller, registry);
  }
}
