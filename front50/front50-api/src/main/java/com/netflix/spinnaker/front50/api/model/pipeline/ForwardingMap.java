package com.netflix.spinnaker.front50.api.model.pipeline;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

abstract class ForwardingMap<K, V> implements Map<K, V> {
  public ForwardingMap() {}

  protected abstract Map<K, V> delegate();

  @Override
  public int size() {
    return delegate().size();
  }

  @Override
  public boolean isEmpty() {
    return delegate().isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return delegate().containsKey(key);
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return delegate().containsValue(value);
  }

  @Override
  public V get(Object key) {
    return delegate().get(key);
  }

  @Override
  public V put(K key, V value) {
    return delegate().put(key, value);
  }

  @Override
  public V remove(Object key) {
    return delegate().remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    delegate().putAll(m);
  }

  @Override
  public void clear() {
    delegate().clear();
  }

  @Override
  public Set<K> keySet() {
    return delegate().keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate().values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return delegate().entrySet();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object == this || delegate().equals(object);
  }

  @Override
  public int hashCode() {
    return delegate().hashCode();
  }
}
