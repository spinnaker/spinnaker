/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.view;

import com.netflix.spinnaker.clouddriver.ecs.cache.client.ApplicationCacheClient;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsApplicationProvider implements ApplicationProvider {

  private final ApplicationCacheClient cacheClient;

  @Autowired
  public EcsApplicationProvider(ApplicationCacheClient cacheClient) {
    this.cacheClient = cacheClient;
  }

  @Override
  public Application getApplication(String name) {
    return cacheClient.getApplication(name);
  }

  @Override
  public Set<Application> getApplications(boolean expand) {
    return cacheClient.getApplications(expand);
  }
}
