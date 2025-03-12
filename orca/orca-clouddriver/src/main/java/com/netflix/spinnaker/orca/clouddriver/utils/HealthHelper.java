/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils;

import static com.netflix.spinnaker.orca.clouddriver.model.HealthState.*;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.model.Health;
import com.netflix.spinnaker.orca.clouddriver.model.HealthState;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import java.util.*;
import java.util.stream.Collectors;

public class HealthHelper {
  /**
   * If stage.context.interestingHealthProviderNames is not null, return it. Otherwise, return
   * providersIfNotExplicitlySpecified. This mechanism enables tasks to have
   * interestingHealthProviderNames be passed in, while still allowing for defaults to be defined.
   * Explicitly-empty lists are preserved and are not ignored in favor of defaults.
   */
  public static List<String> getInterestingHealthProviderNames(
      StageExecution stage, List<String> providersIfNotExplicitlySpecified) {
    List<String> result = (List<String>) stage.getContext().get("interestingHealthProviderNames");
    return result != null ? result : providersIfNotExplicitlySpecified;
  }

  /**
   * If interestingHealthProviderNames is not null, return all matching healths (that is, return the
   * intersection of the health providers deemed interesting and the healths defined on the
   * instance). Otherwise, return all healths defined on the instance.
   */
  public static List<Health> filterHealths(
      Instance instance, Collection<String> interestingHealthProviderNames) {
    List<Health> healths = instance.getHealth();
    if (healths == null) {
      return List.of();
    }
    if (interestingHealthProviderNames == null) {
      return healths;
    }
    return healths.stream()
        .filter(health -> interestingHealthProviderNames.contains(health.getType()))
        .collect(Collectors.toList());
  }

  /**
   * Return true if platform health was specified in interestingHealthProviderNames, and its state
   * is not 'Down'. Otherwise, return the value of someAreUp.
   */
  private static boolean areSomeUpConsideringPlatformHealth(
      List<Health> healths, Collection<String> interestingHealthProviderNames, boolean someAreUp) {

    if (interestingHealthProviderNames == null || someAreUp) {
      return someAreUp;
    }

    return findPlatformHealth(healths)
        .filter(it -> interestingHealthProviderNames.contains(it.getType()))
        .map(platformHealth -> Down != platformHealth.getState())
        .orElse(someAreUp);
  }

  /**
   * Return true if there is exactly one (filtered) health, it is a platform health, and its state
   * is 'Unknown'. Otherwise, return false.
   */
  private static boolean isDownConsideringPlatformHealth(List<Health> healths) {
    if (healths.size() != 1) {
      return false;
    }
    Health health = healths.get(0);

    return findPlatformHealth(healths)
        .map(
            platformHealth ->
                Objects.equals(health.getType(), platformHealth.getType())
                    && Unknown == health.getState())
        .orElse(false);
  }

  /** Return the first health with a healthClass of 'platform', or null if none is found. */
  private static Optional<Health> findPlatformHealth(List<Health> healths) {
    return healths.stream()
        .filter(health -> "platform".equals(health.getHealthClass()))
        .findFirst();
  }

  public static boolean someAreDownAndNoneAreUp(
      Instance instance, Collection<String> interestingHealthProviderNames) {
    List<Health> healths = filterHealths(instance, interestingHealthProviderNames);

    if ((interestingHealthProviderNames == null || interestingHealthProviderNames.isEmpty())
        && healths.isEmpty()) {
      // No health indications (and no specific providers to check), consider instance to be down.
      return true;
    }

    if (isDownConsideringPlatformHealth(healths)) {
      return true;
    }

    Set<HealthState> downStates = Set.of(Down, OutOfService, Starting);
    Set<HealthState> upStates = Set.of(Up, Draining);

    // no health indicators is indicative of being down
    boolean someAreDown =
        healths.isEmpty() || healths.stream().map(Health::getState).anyMatch(downStates::contains);
    boolean noneAreUp = healths.stream().map(Health::getState).noneMatch(upStates::contains);

    return someAreDown && noneAreUp;
  }

  public static boolean someAreUpAndNoneAreDownOrStarting(
      Instance instance, Collection<String> interestingHealthProviderNames) {
    List<Health> healths = filterHealths(instance, interestingHealthProviderNames);

    Set<HealthState> downStates = Set.of(Down, OutOfService, Starting, Draining);

    boolean someAreUp = healths.stream().anyMatch(it -> it.getState() == Up);
    boolean noneAreDown = healths.stream().map(Health::getState).noneMatch(downStates::contains);

    return areSomeUpConsideringPlatformHealth(healths, interestingHealthProviderNames, someAreUp)
        && noneAreDown;
  }
}
