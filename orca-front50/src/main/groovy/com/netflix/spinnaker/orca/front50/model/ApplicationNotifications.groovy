/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter

class ApplicationNotifications {
  String application
  String lastModifiedBy
  Long lastModified

  private Map<String, Collection<Notification>> notificationsByType = new HashMap<String, Collection<Notification>>()

  @JsonAnyGetter
  Map<String, Collection<Notification>> details() {
    return notificationsByType
  }

  @JsonAnySetter
  void set(String name, Collection<Notification> value) {
    notificationsByType.put(name, value)
  }

  List<Map<String, Object>> getPipelineNotifications() {
    notificationsByType.values().flatten()
      .findAll { it.getWhen().any { it.startsWith("pipeline") }}
      .findResults { notification ->
        notification.when = notification.getWhen().findAll { it.startsWith("pipeline") }
        notification
      }
  }

  static class Notification extends HashMap<String, Object> {
    Notification() {}
    Notification(Map<String, Object> config) {
      super(config)
    }
    List<String> getWhen() {
      (List<String>) super.get("when") ?: []
    }
    String getType() {
      (String) super.get("type")
    }
  }
}
