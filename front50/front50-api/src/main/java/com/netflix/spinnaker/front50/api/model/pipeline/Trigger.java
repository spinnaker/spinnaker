/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.api.model.pipeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Trigger extends ForwardingMap<String, Object> {

  private final Map<String, Object> backing = new HashMap<>();

  @Override
  protected Map<String, Object> delegate() {
    return backing;
  }

  public Boolean getEnabled() {
    Object enabled = backing.get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) enabled;
    }

    // orca's DependentPipelineExecutionListener.groovy currently (18-apr-23)
    // has code like
    //
    // if (trigger.enabled)
    //
    // which means any non-null, non-empty string/map/list/non-zero number is
    // truthy.  See https://groovy-lang.org/semantics.html#the-groovy-truth.
    // It's likely worth changing orca as this is not the most obvious behavior.
    // Whether orca changes or not, it doesn't seem right to duplicate that
    // non-obvious behavior here.
    //
    // Perhaps the most surprising part of this behavior is that a string
    // "false" is interpreted at truthy.  To that end, let's add special
    // handling for strings so "false" is considered false, and "true" is
    // considered true.
    //
    // Also note that if we stop extending ForwardingMap here and convert to a
    // "proper" pojo, this method would likely disappear and jackson would take
    // over, handling true/True/TRUE and false/False/FALSE and throwing an
    // exception otherwise.
    //
    // Getting an objectMapper here to invoke explicitly would be nice in that
    // we'd only have one behavior change, but code-wise it feels
    // cumbersome.
    //
    // So, let's try to make the trajectory here from less restrictive to more
    // restrictive without then becoming less restrictive in the future.  In
    // other words, we start by considering the most triggers enabled, and
    // reduce that over time, but once a trigger is considered disabled, it
    // doesn't magically become enabled again.  So, let's consider at least as
    // many triggers enabled as jackson would.
    if (enabled instanceof String) {
      // This allows not only true/True/TRUE but also some values that groovy
      // considers truthy, e.g. tRue, tRUe, etc.
      return Boolean.valueOf(((String) enabled).toLowerCase());
    }

    if (enabled != null) {
      log.warn("enabled: '{}' is a {}, not a Boolean", enabled, enabled.getClass().getSimpleName());
    }
    return false;
  }

  /** For triggers of type "pipeline", this is the id of the triggering pipeline. null otherwise. */
  public String getPipeline() {
    return (String) backing.get("pipeline");
  }

  public List<String> getStatus() {
    Object status = backing.get("status");
    if (status instanceof List) {
      return List.copyOf((List<String>) status);
    }

    if (status != null) {
      log.warn("status: '{}' is a {}, not a List", status, status.getClass().getSimpleName());
    }
    return Collections.emptyList();
  }

  public String getType() {
    return (String) backing.get("type");
  }

  public void setType(String type) {
    backing.put("type", type);
  }
}
