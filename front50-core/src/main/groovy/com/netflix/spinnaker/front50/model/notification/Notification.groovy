/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.model.notification

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.front50.model.Timestamped

class Notification extends HashMap<String, Object> implements Timestamped {
  public static final String GLOBAL_ID = "__GLOBAL"

  @Override
  @JsonIgnore
  String getId() {
    return super.get("application") ?: GLOBAL_ID
  }

  @Override
  @JsonIgnore
  Long getLastModified() {
    return (Long) super.get("lastModified")
  }

  @Override
  void setLastModified(Long lastModified) {
    super.put("lastModified", lastModified)
  }

  @Override
  String getLastModifiedBy() {
    return super.get("lastModifiedBy")
  }

  @Override
  void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy)
  }
}
