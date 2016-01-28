/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.helpers

import com.netflix.frigga.NameBuilder
import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode

abstract class AbstractServerGroupNameResolver extends NameBuilder {

  static final int SEQUENTIAL_NUMBERING_NAMESPACE_SIZE = 1000

  /**
   * Returns the phase of this task
   */
  abstract String getPhase()

  /**
   * Returns the region within which to resolve the next server group name
   */
  abstract String getRegion()

  /**
   * Returns the taken naming slots for the given cluster name
   */
  abstract List<TakenSlot> getTakenSlots(String clusterName)

  static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  String resolveLatestServerGroupName(String clusterName, List<TakenSlot> takenSlots = []) {
    takenSlots = takenSlots ?: getTakenSlots(clusterName)

    // Attempt to find the server group created most recently.
    def latestServerGroup = takenSlots?.max { a, b ->
      a.createdTime <=> b.createdTime
    }

    return latestServerGroup ? latestServerGroup.serverGroupName : null
  }

  String resolveNextServerGroupName(String application, String stack, String details, Boolean ignoreSequence) {
    Integer nextSequence = 0
    String clusterName = combineAppStackDetail(application, stack, details)
    List<TakenSlot> takenSlots = getTakenSlots(clusterName)

    def latestServerGroup = takenSlots.find { it.serverGroupName == resolveLatestServerGroupName(clusterName, takenSlots) }
    if (latestServerGroup) {
      getTask().updateStatus phase, "Found ancestor server group, parsing details (name: $latestServerGroup.serverGroupName)"
      Map ancestorServerGroupNameByRegion = [ancestorServerGroupNameByRegion: [(region): latestServerGroup.serverGroupName]]
      getTask().addResultObjects([ancestorServerGroupNameByRegion])

      // The server group name may not have the sequence number portion specified.
      nextSequence = incrementSequence(latestServerGroup.sequence)
    }

    // Keep increasing the number until we find one that is not already taken. Stop if we circle back to the starting point.
    def stepCounter = 0
    while (takenSlots.find { it.sequence == nextSequence } && ++stepCounter < SEQUENTIAL_NUMBERING_NAMESPACE_SIZE) {
      nextSequence = ++nextSequence % SEQUENTIAL_NUMBERING_NAMESPACE_SIZE
    }

    if (stepCounter == SEQUENTIAL_NUMBERING_NAMESPACE_SIZE) {
      throw new IllegalArgumentException("All server group names for cluster $clusterName in $region are taken.")
    }
    return generateServerGroupName(application, stack, details, nextSequence, ignoreSequence)
  }

  protected int generateNextSequence(String serverGroupName) {
    Names parts = Names.parseName(serverGroupName)
    return incrementSequence(parts.sequence)
  }

  private static int incrementSequence(Long sequence) {
    return ((sequence ?: 0) + 1) % SEQUENTIAL_NUMBERING_NAMESPACE_SIZE
  }

  static String generateServerGroupName(String application, String stack, String details, Integer sequence, Boolean ignoreSequence) {
    def builder = new AutoScalingGroupNameBuilder(appName: application, stack: stack, detail: details)
    def groupName = builder.buildGroupName(true)
    if (ignoreSequence) {
      return groupName
    }
    String.format("%s-v%03d", groupName, sequence)
  }

  @Canonical
  @EqualsAndHashCode
  static class TakenSlot {
    String serverGroupName
    Integer sequence
    Date createdTime
  }
}
