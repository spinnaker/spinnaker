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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;

import com.amazonaws.services.ec2.model.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EcsInstanceCacheClient {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  public EcsInstanceCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  public Set<Instance> findAll() {
    return find("*", "*", "*");
  }

  public Set<Instance> find(String instanceId, String account, String region) {
    instanceId = instanceId != null ? instanceId : "*";
    account = account != null ? account : "*";
    region = region != null ? region : "*";

    String searchKey = Keys.getInstanceKey(instanceId, account, region);
    Collection<String> instanceKeys = cacheView.filterIdentifiers(INSTANCES.getNs(), searchKey);

    return cacheView.getAll(INSTANCES.getNs(), instanceKeys).stream()
        .map(cacheData -> objectMapper.convertValue(cacheData.getAttributes(), Instance.class))
        .collect(Collectors.toSet());
  }
}
