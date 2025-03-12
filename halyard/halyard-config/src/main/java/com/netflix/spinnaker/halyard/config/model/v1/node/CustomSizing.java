/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A CustomSizing is a map of maps where you can hack in provider-specific settings related to
 * instance/container/pod sizes.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CustomSizing implements Map<String, Map> {
  Map<String, Map> componentSizings = new HashMap<>();

  public static String stringOrNull(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  @Override
  public int size() {
    return componentSizings.size();
  }

  @Override
  public boolean isEmpty() {
    return componentSizings.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return componentSizings.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return componentSizings.containsValue(value);
  }

  @Override
  public Map get(Object key) {
    return componentSizings.get(key);
  }

  @Override
  public Map put(String key, Map value) {
    return componentSizings.put(key, value);
  }

  @Override
  public Map remove(Object key) {
    return componentSizings.remove(key);
  }

  @Override
  public void putAll(Map m) {
    componentSizings.putAll(m);
  }

  @Override
  public void clear() {
    componentSizings.clear();
  }

  @Override
  public Set<String> keySet() {
    return componentSizings.keySet();
  }

  @Override
  public Collection<Map> values() {
    return componentSizings.values();
  }

  @Override
  public Set<Entry<String, Map>> entrySet() {
    return componentSizings.entrySet();
  }

  public boolean hasCustomSizing(String service) {
    return get(service) != null;
  }
}
