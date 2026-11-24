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
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spinnaker.fiat.config.ProviderCacheConfig;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public abstract class BaseResourceProvider<R extends Resource> implements ResourceProvider<R> {

  private static final Integer CACHE_KEY = 0;

  private Cache<Integer, ResourceSnapshot<R>> cache = buildCache(20);

  @Override
  public Set<R> getAllRestricted(
      @NonNull String userId, @NonNull Set<Role> userRoles, boolean isAdmin)
      throws ProviderException {
    ResourceSnapshot<R> snapshot = getSnapshot();
    if (isAdmin) {
      return snapshot.restricted;
    }
    Set<R> result = new HashSet<>();
    for (Role role : userRoles) {
      result.addAll(snapshot.restrictedByGroup.get(role.getName()));
    }
    return result;
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
    return getSnapshot().all;
  }

  private ResourceSnapshot<R> getSnapshot() throws ProviderException {
    try {
      return cache.get(CACHE_KEY, () -> new ResourceSnapshot<>(loadAll()));
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

  private Cache<Integer, ResourceSnapshot<R>> buildCache(int expireAfterWrite) {
    return CacheBuilder.newBuilder()
        .expireAfterWrite(expireAfterWrite, TimeUnit.SECONDS)
        .maximumSize(1) // Using this cache loader just for the ability to refresh every X seconds.
        .build();
  }

  @Scheduled(fixedRateString = "${fiat.cache.refresh-interval:PT15S}")
  public void reloadCache() {
    Set<R> data = loadAll();
    cache.put(CACHE_KEY, new ResourceSnapshot<>(data));
  }

  public void clearCache() {
    cache.invalidate(CACHE_KEY);
  }

  protected abstract Set<R> loadAll() throws ProviderException;

  /**
   * Loaded resources together with the restricted access-controlled subset and an index of that
   * subset by permission group, cached as one value so the index always matches the resources.
   */
  private static final class ResourceSnapshot<R extends Resource> {
    private final ImmutableSet<R> all;
    private final ImmutableSet<R> restricted;
    private final ImmutableSetMultimap<String, R> restrictedByGroup;

    private ResourceSnapshot(Set<R> resources) {
      this.all = ImmutableSet.copyOf(resources);
      ImmutableSet.Builder<R> restrictedBuilder = ImmutableSet.builder();
      ImmutableSetMultimap.Builder<String, R> byGroupBuilder = ImmutableSetMultimap.builder();
      for (R resource : all) {
        if (!(resource instanceof Resource.AccessControlled)) {
          continue;
        }
        Permissions permissions = ((Resource.AccessControlled) resource).getPermissions();
        if (permissions == null || !permissions.isRestricted()) {
          continue;
        }
        restrictedBuilder.add(resource);
        // Groups are already trimmed and lowercased by Permissions.Builder, matching Role names.
        for (String group : permissions.allGroups()) {
          byGroupBuilder.put(group, resource);
        }
      }
      this.restricted = restrictedBuilder.build();
      this.restrictedByGroup = byGroupBuilder.build();
    }
  }
}
