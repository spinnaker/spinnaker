/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.common;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

public class HealthHelper {

  private static boolean healthyStateMatcher(String key, String loadBalancerId, String instanceId) {
    String regex;
    if (StringUtils.isNotBlank(loadBalancerId)) {
      regex = AliCloudProvider.ID + ":.*:" + loadBalancerId + ":" + instanceId + ":.*";
      return Pattern.matches(regex, key);
    } else {
      regex = AliCloudProvider.ID + ":.*:" + instanceId + ":.*";
    }
    return Pattern.matches(regex, key);
  }

  public static HealthState judgeInstanceHealthyState(
      Collection<String> allHealthyKeys,
      List<String> loadBalancerIds,
      String instanceId,
      Cache cacheView) {
    Set<String> healthyKeys = new HashSet<>();
    if (loadBalancerIds != null) {
      for (String loadBalancerId : loadBalancerIds) {
        List<String> collect =
            allHealthyKeys.stream()
                .filter(tab -> HealthHelper.healthyStateMatcher(tab, loadBalancerId, instanceId))
                .collect(Collectors.toList());
        Collection<CacheData> healthData = cacheView.getAll(HEALTH.ns, collect, null);
        if (CollectionUtils.isEmpty(healthData)) {
          return HealthState.Unknown;
        }
        healthyKeys.addAll(collect);
      }
    } else {
      List<String> collect =
          allHealthyKeys.stream()
              .filter(tab -> HealthHelper.healthyStateMatcher(tab, null, instanceId))
              .collect(Collectors.toList());
      healthyKeys.addAll(collect);
    }
    Collection<CacheData> healthData = cacheView.getAll(HEALTH.ns, healthyKeys, null);
    Map<String, Integer> healthMap = new HashMap<>(16);
    for (CacheData cacheData : healthData) {
      String serverHealthStatus = cacheData.getAttributes().get("serverHealthStatus").toString();
      healthMap.put(serverHealthStatus, healthMap.getOrDefault(serverHealthStatus, 0) + 1);
    }
    Integer normal = healthMap.get("normal");
    Integer abnormal = healthMap.get("abnormal");
    if (normal != null && normal > 0 && abnormal == null) {
      return HealthState.Up;
    } else if (abnormal != null && abnormal > 0 && normal == null) {
      return HealthState.Down;
    } else if (abnormal == null && normal == null) {
      return HealthState.Down;
    } else {
      return HealthState.Unknown;
    }
  }
}
