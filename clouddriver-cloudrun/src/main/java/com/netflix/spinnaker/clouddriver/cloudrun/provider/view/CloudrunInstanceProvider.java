/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunInstance;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudrunInstanceProvider implements InstanceProvider<CloudrunInstance, String> {
  @Autowired Cache cacheView;

  @Autowired ObjectMapper objectMapper;

  @Override
  public String getCloudProvider() {
    return CloudrunCloudProvider.ID;
  }

  @Override
  public CloudrunInstance getInstance(String account, String region, String instanceName) {
    String instanceKey = Keys.getInstanceKey(account, instanceName);
    CacheData instanceData =
        cacheView.get(
            INSTANCES.getNs(),
            instanceKey,
            RelationshipCacheFilter.include(LOAD_BALANCERS.getNs(), SERVER_GROUPS.getNs()));
    if (instanceData == null) {
      return null;
    }
    return getInstanceFromCacheData(instanceData);
  }

  private CloudrunInstance getInstanceFromCacheData(CacheData cacheData) {
    return objectMapper.convertValue(
        cacheData.getAttributes().get("instance"), CloudrunInstance.class);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }
}
