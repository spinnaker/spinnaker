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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;

import java.util.HashMap;

public class NamedHashMap extends HashMap<String, Object> implements NamedContent<NamedHashMap> {

  public NamedHashMap() {}

  private NamedHashMap(NamedHashMap m) {
    super(m);
  }

  @Override
  public String getName() {
    return String.valueOf(get("name"));
  }

  @Override
  public boolean isRemove() {
    return Boolean.parseBoolean(String.valueOf(getOrDefault("remove", "false")));
  }

  @Override
  public boolean isMerge() {
    return Boolean.parseBoolean(String.valueOf(getOrDefault("merge", "false")));
  }

  @Override
  public NamedHashMap merge(NamedHashMap overlay) {
    NamedHashMap m = new NamedHashMap(this);
    m.putAll(overlay);
    m.remove("merge");
    return m;
  }
}
