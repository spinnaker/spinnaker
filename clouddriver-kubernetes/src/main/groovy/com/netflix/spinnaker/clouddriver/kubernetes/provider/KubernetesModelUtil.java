/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.provider;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Slf4j
public class KubernetesModelUtil {
  public static long translateTime(String time) {
    return KubernetesModelUtil.translateTime(time, "yyyy-MM-dd'T'HH:mm:ssX");
  }

  public static long translateTime(String time, String format) {
    try {
      return StringUtils.isNotEmpty(time)
          ? (new SimpleDateFormat(format).parse(time)).getTime()
          : 0;
    } catch (ParseException e) {
      log.error("Failed to parse kubernetes timestamp", e);
      return 0;
    }
  }

  public static HealthState getHealthState(List<Map<String, Object>> health) {
    return someUpRemainingUnknown(health)
        ? HealthState.Up
        : someSucceededRemainingUnknown(health)
            ? HealthState.Succeeded
            : anyStarting(health)
                ? HealthState.Starting
                : anyDown(health)
                    ? HealthState.Down
                    : anyFailed(health)
                        ? HealthState.Failed
                        : anyOutOfService(health) ? HealthState.OutOfService : HealthState.Unknown;
  }

  private static boolean stateEquals(Map<String, Object> health, HealthState state) {
    Object healthState = health.get("state");
    return healthState != null && healthState.equals(state.name());
  }

  private static boolean someUpRemainingUnknown(List<Map<String, Object>> healthsList) {
    List<Map<String, Object>> knownHealthList =
        healthsList.stream()
            .filter(h -> !stateEquals(h, HealthState.Unknown))
            .collect(Collectors.toList());

    return !knownHealthList.isEmpty()
        && knownHealthList.stream().allMatch(h -> stateEquals(h, HealthState.Up));
  }

  private static boolean someSucceededRemainingUnknown(List<Map<String, Object>> healthsList) {
    List<Map<String, Object>> knownHealthList =
        healthsList.stream()
            .filter(h -> !stateEquals(h, HealthState.Unknown))
            .collect(Collectors.toList());

    return !knownHealthList.isEmpty()
        && knownHealthList.stream().allMatch(h -> stateEquals(h, HealthState.Succeeded));
  }

  private static boolean anyDown(List<Map<String, Object>> healthsList) {
    return healthsList.stream().anyMatch(h -> stateEquals(h, HealthState.Down));
  }

  private static boolean anyStarting(List<Map<String, Object>> healthsList) {
    return healthsList.stream().anyMatch(h -> stateEquals(h, HealthState.Starting));
  }

  private static boolean anyFailed(List<Map<String, Object>> healthsList) {
    return healthsList.stream().anyMatch(h -> stateEquals(h, HealthState.Failed));
  }

  private static boolean anyOutOfService(List<Map<String, Object>> healthsList) {
    return healthsList.stream().anyMatch(h -> stateEquals(h, HealthState.OutOfService));
  }
}
