/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.endpoints

import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.persistence.BakeStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class StatusHandler {

  private final BakeStore bakeStore
  private final String roscoInstanceId

  private static enum Status {
    RUNNING, IDLE
  }

  @Autowired
  StatusHandler(BakeStore bakeStore, String roscoInstanceId) {
    this.bakeStore = bakeStore
    this.roscoInstanceId = roscoInstanceId
  }

  public Map<String, Object> instanceIncompleteBakes() {
    def instanceIncompleteBakeIds = bakeStore.getThisInstanceIncompleteBakeIds()
    return getBakesAndInstanceStatus(instanceIncompleteBakeIds)
  }

  public Map<String, Object> AllIncompleteBakes() {
    def instances = [:]
    def allIncompleteBakeIds = bakeStore.getAllIncompleteBakeIds()
    if (allIncompleteBakeIds) {
      for (instanceEntry in allIncompleteBakeIds.entrySet()) {
        instances[instanceEntry.key] = getBakesAndInstanceStatus(instanceEntry.value)
      }
    }
    return Collections.unmodifiableMap(["instance": roscoInstanceId, "instances": instances])
  }

  private Map<String, Object> getBakesAndInstanceStatus(Set<String> instanceIncompleteBakeIds) {
    def instanceStatus, bakes
    if (instanceIncompleteBakeIds) {
      instanceStatus = Status.RUNNING
      bakes = getBakes(instanceIncompleteBakeIds)
    } else {
      instanceStatus = Status.IDLE
      bakes = []
    }
    return Collections.unmodifiableMap(["status": instanceStatus.name(), "bakes": bakes])
  }

  private List<BakeStatus> getBakes(instanceIncompleteBakeIds) {
    def status = []
    for (bakeId in instanceIncompleteBakeIds) {
      def bakeStatus = bakeStore.retrieveBakeStatusById(bakeId)
      if (bakeStatus) {
        status << bakeStatus
      }
    }
    return status
  }

}
