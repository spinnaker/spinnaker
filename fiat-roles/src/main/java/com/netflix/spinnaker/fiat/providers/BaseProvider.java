/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.providers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spinnaker.fiat.config.ProviderCacheConfig;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class BaseProvider<R extends Resource> implements ResourceProvider<R> {

  private static final Integer CACHE_KEY = 0;

  private Cache<Integer, Set<R>> cache = buildCache(20);

  @Override
  @SuppressWarnings("unchecked")
  public Set<R> getAllRestricted(@NonNull Set<Role> roles, boolean isAdmin)
      throws ProviderException {
    return (Set<R>)
        getAll().stream()
            .filter(resource -> resource instanceof Resource.AccessControlled)
            .map(resource -> (Resource.AccessControlled) resource)
            .filter(resource -> resource.getPermissions().isRestricted())
            .filter(resource -> resource.getPermissions().isAuthorized(roles) || isAdmin)
            .collect(Collectors.toSet());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<R> getAllUnrestricted() throws ProviderException {
    return (Set<R>)
        getAll().stream()
            .filter(resource -> resource instanceof Resource.AccessControlled)
            .map(resource -> (Resource.AccessControlled) resource)
            .filter(resource -> !resource.getPermissions().isRestricted())
            .collect(Collectors.toSet());
  }

  @Override
  public Set<R> getAll() throws ProviderException {
    try {
      return ImmutableSet.copyOf(cache.get(CACHE_KEY, this::loadAll));
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof ProviderException) {
        throw (ProviderException) e.getCause();
      }
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }

  @Autowired
  public void setProviderCacheConfig(ProviderCacheConfig config) {
    this.cache = buildCache(config.getExpiresAfterWriteSeconds());
  }

  private Cache<Integer, Set<R>> buildCache(int expireAfterWrite) {
    return CacheBuilder.newBuilder()
        .expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS)
        .maximumSize(1) // Using this cache loader just for the ability to refresh every X seconds.
        .build();
  }

  protected abstract Set<R> loadAll() throws ProviderException;
}
