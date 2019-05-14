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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsClusterCacheClient extends AbstractCacheClient<EcsCluster> {

  @Autowired
  public EcsClusterCacheClient(Cache cacheView) {
    super(cacheView, ECS_CLUSTERS.toString());
  }

  @Override
  protected EcsCluster convert(CacheData cacheData) {
    EcsCluster ecsCluster = new EcsCluster();
    Map<String, Object> attributes = cacheData.getAttributes();

    ecsCluster.setAccount((String) attributes.get("account"));
    ecsCluster.setRegion((String) attributes.get("region"));
    ecsCluster.setName((String) attributes.get("clusterName"));
    ecsCluster.setArn((String) attributes.get("clusterArn"));

    return ecsCluster;
  }
}
