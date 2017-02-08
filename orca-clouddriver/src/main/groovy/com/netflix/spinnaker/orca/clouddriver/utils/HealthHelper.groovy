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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.netflix.spinnaker.orca.pipeline.model.Stage

class HealthHelper {
  /**
   * If stage.context.interestingHealthProviderNames is not null, return it. Otherwise, return
   * providersIfNotExplicitlySpecified. This mechanism enables tasks to have interestingHealthProviderNames be passed
   * in, while still allowing for defaults to be defined. Explicitly-empty lists are preserved and are not ignored in
   * favor of defaults.
   */
  static List<String> getInterestingHealthProviderNames(Stage stage, List<String> providersIfNotExplicitlySpecified) {
    stage.context.interestingHealthProviderNames != null ?
      stage.context.interestingHealthProviderNames : providersIfNotExplicitlySpecified
  }

  /**
   * If interestingHealthProviderNames is not null, return all matching healths (that is, return the intersection of the
   * health providers deemed interesting and the healths defined on the instance). Otherwise, return all healths defined
   * on the instance.
   */
  static List<Map> filterHealths(Map instance, Collection<String> interestingHealthProviderNames) {
    return interestingHealthProviderNames != null ? instance.health.findAll { health ->
      health.type in interestingHealthProviderNames
    } : instance.health
  }

  /**
   * Return true if platform health was specified in interestingHealthProviderNames, and its state is not 'Down'.
   * Otherwise, return the value of someAreUp.
   */
  static boolean areSomeUpConsideringPlatformHealth(List<Map> healths,
                                                    Collection<String> interestingHealthProviderNames,
                                                    boolean someAreUp) {
   Map platformHealth = findPlatformHealth(healths)

   if (platformHealth && interestingHealthProviderNames?.contains(platformHealth.type)) {
     // Given that platform health (e.g. 'Amazon' or 'GCE') never reports as 'Up' (only 'Unknown') we can only verify it
     // isn't 'Down'.
     someAreUp = someAreUp || platformHealth.state != 'Down'
   }

   return someAreUp
 }

  /**
   * Return true if there is exactly one (filtered) health, it is a platform health, and its state is 'Unknown'.
   * Otherwise, return false.
   */
  static boolean isDownConsideringPlatformHealth(List<Map> healths) {
    Map platformHealth = findPlatformHealth(healths)

    if (platformHealth && healths.size() == 1 && healths[0].type == platformHealth.type && healths[0].state == "Unknown") {
      return true
    }

    return false
  }

  /**
   * Return the first health with a healthClass of 'platform', or null if none is found.
   */
  static Map findPlatformHealth(List<Map> healths) {
    return healths.find { Map health ->
      health.healthClass == 'platform'
    } as Map
  }

  static boolean someAreDownAndNoneAreUp(Map instance, Collection<String> interestingHealthProviderNames) {

    List<Map> healths = filterHealths(instance, interestingHealthProviderNames)

    if (!interestingHealthProviderNames && !healths) {
      // No health indications (and no specific providers to check), consider instance to be down.
      return true
    }

    if (isDownConsideringPlatformHealth(healths)) {
      return true
    }

    // no health indicators is indicative of being down
    boolean someAreDown = !healths || healths.any { it.state == 'Down' || it.state == 'OutOfService' }
    boolean noneAreUp = !healths.any { it.state == 'Up' }

    return someAreDown && noneAreUp
  }

  static boolean someAreUpAndNoneAreDown(Map instance, Collection<String> interestingHealthProviderNames) {
    List<Map> healths = filterHealths(instance, interestingHealthProviderNames)
    boolean someAreUp = healths.any { Map health -> health.state == 'Up' }
    someAreUp = areSomeUpConsideringPlatformHealth(healths, interestingHealthProviderNames, someAreUp)

    boolean noneAreDown = !healths.any { Map health -> health.state == 'Down' }
    return someAreUp && noneAreDown
  }

  static class HealthCountSnapshot {
    int up
    int down
    int outOfService
    int starting
    int succeeded
    int failed
    int unknown
  }

}
