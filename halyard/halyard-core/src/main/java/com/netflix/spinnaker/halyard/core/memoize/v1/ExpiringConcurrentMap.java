/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.core.memoize.v1;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpiringConcurrentMap<K, V> implements Map<K, V> {
  private final long timeout;

  private final ConcurrentHashMap<K, ExpiringConcurrentMap.Entry> delegate;

  public static ExpiringConcurrentMap fromMillis(long timeout) {
    return new ExpiringConcurrentMap(timeout);
  }

  public static ExpiringConcurrentMap fromMinutes(long timeout) {
    return new ExpiringConcurrentMap(TimeUnit.MINUTES.toMillis(timeout));
  }

  private ExpiringConcurrentMap(long timeout) {
    this.timeout = timeout;
    this.delegate = new ConcurrentHashMap<>();
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  @Override
  public boolean containsValue(Object value) {
    return delegate.values().stream().anyMatch(v -> v.value.equals(value) && !v.expired());
  }

  @Override
  public V get(Object key) {
    Entry e = delegate.get(key);
    if (e == null) {
      return null;
    } else if (e.expired()) {
      log.info("Removing expired key: " + key);
      delegate.remove(key);
      return null;
    } else {
      return e.value;
    }
  }

  @Override
  public V put(K key, V value) {
    delegate.put(key, new Entry(value));
    return value;
  }

  @Override
  public V remove(Object key) {
    return (V) delegate.remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    m.entrySet().forEach(es -> put(es.getKey(), es.getValue()));
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public Set<K> keySet() {
    return entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toSet());
  }

  @Override
  public Collection<V> values() {
    return entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toList());
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry> result =
        delegate.entrySet().stream()
            .filter(e -> !e.getValue().expired())
            .collect(Collectors.toSet());

    return result.stream()
        .map(e -> new AbstractMap.SimpleEntry<>((K) e.getKey(), ((Entry) e.getValue()).value))
        .collect(Collectors.toSet());
  }

  private class Entry {
    long lastUpdate;
    V value;

    public Entry() {}

    public Entry(V value) {
      this.value = value;
      this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public String toString() {
      return "lastUpdate: " + lastUpdate + ", value: " + value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      } else if (!Entry.class.isAssignableFrom(obj.getClass())) {
        return false;
      }

      Entry other = (Entry) obj;

      if (other.value == null) {
        return value == null;
      } else {
        return other.value.equals(value);
      }
    }

    boolean expired() {
      return System.currentTimeMillis() - lastUpdate > timeout;
    }
  }
}
