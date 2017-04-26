/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.front50.model.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.front50.model.Timestamped;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineTemplate extends HashMap<String, Object> implements Timestamped {

  @JsonIgnore
  @SuppressWarnings("unchecked")
  public List<String> getScopes() {
    Map<String, Object> metadata = (Map<String, Object>) super.get("metadata");
    if (metadata == null || metadata.isEmpty()) {
      return Collections.emptyList();
    }
    return (List<String>) metadata.get("scopes");
  }

  @Override
  public String getId() {
    return (String) super.get("id");
  }

  @Override
  public Long getLastModified() {
    String updateTs = (String) super.get("updateTs");
    return (updateTs != null) ? Long.valueOf(updateTs) : null;
  }

  @Override
  public void setLastModified(Long lastModified) {
    super.put("updateTs", lastModified.toString());
  }

  @Override
  public String getLastModifiedBy() {
    return (String) super.get("lastModifiedBy");
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy);
  }

  public boolean containsAnyScope(List<String> scope) {
    for (String s : scope) {
      for (String s2 : getScopes()) {
        if (s.equalsIgnoreCase(s2)) {
          return true;
        }
      }
    }
    return false;
  }
}
