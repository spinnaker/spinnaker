/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.security.roles;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spinnaker.security.authz.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link UserRolesProvider} base implementation providing optional caching for user roles. */
public abstract class BaseUserRolesProvider implements UserRolesProvider {

  /** Cache loader implementation consistent with {@link UserRolesProvider}. */
  class BaseUserRolesProviderCacheLoader extends CacheLoader<String, Collection<Role>> {
    @Override
    public Collection<Role> load(final @Nonnull String userId) {
      return loadRolesForUser(new ExternalUser().setId(userId));
    }

    @Override
    public Map<String, Collection<Role>> loadAll(@Nonnull Iterable<? extends String> userIds) {
      final List<ExternalUser> externalUsers =
          ImmutableList.copyOf(userIds).stream()
              .map(userId -> new ExternalUser().setId(userId))
              .collect(Collectors.toList());
      return loadRolesForUsers(externalUsers);
    }
  }

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected boolean cacheEnabled;
  protected LoadingCache<String, Collection<Role>> loadingCache;

  public final void setProviderCacheConfig(final UserRolesProviderCacheConfig cacheConfig) {
    final boolean enableCache = cacheConfig != null && cacheConfig.isEnabled();
    final long expireAfterWriteSeconds =
        enableCache ? cacheConfig.getExpireAfterWriteSeconds() : 0L;

    this.loadingCache = enableCache ? buildCache(expireAfterWriteSeconds) : null;
    this.cacheEnabled = enableCache;

    log.info(
        "Caching status: enabled = {}, expireAfterWriteSeconds = {}",
        enableCache,
        expireAfterWriteSeconds);
  }

  private LoadingCache<String, Collection<Role>> buildCache(final long expireAfterWriteSeconds) {
    return CacheBuilder.newBuilder()
        .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
        .build(new BaseUserRolesProviderCacheLoader());
  }

  @Override
  public List<Role> loadRoles(final ExternalUser user) {
    try {
      if (cacheEnabled) {
        final Collection<Role> roles = loadingCache.get(user.getId());
        return roles instanceof List<?> ? (List<Role>) roles : new ArrayList<>(roles);
      } else {
        return loadRolesForUser(user);
      }
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof ProviderException) {
        throw (ProviderException) e.getCause();
      }
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }

  private List<String> convertExternalUsersToUserIds(final Iterable<ExternalUser> users) {
    return StreamSupport.stream(users.spliterator(), false)
        .map(ExternalUser::getId)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(final Collection<ExternalUser> users) {
    try {
      if (cacheEnabled) {
        return new HashMap<>(loadingCache.getAll(convertExternalUsersToUserIds(users)));
      } else {
        return loadRolesForUsers(users);
      }
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof ProviderException) {
        throw (ProviderException) e.getCause();
      }
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }

  /** Loads roles for a single user. Must be implemented to support cache loading. */
  protected abstract List<Role> loadRolesForUser(final ExternalUser user) throws ProviderException;

  /**
   * Loads roles for multiple users. The default implementation loads users one at a time; override
   * when bulk retrieval is significantly more efficient.
   */
  protected Map<String, Collection<Role>> loadRolesForUsers(final Collection<ExternalUser> users)
      throws ProviderException {
    return users.stream().collect(Collectors.toMap(ExternalUser::getId, this::loadRolesForUser));
  }

  /** Invalidates a single user entry in the cache. */
  public void invalidate(final ExternalUser user) {
    if (cacheEnabled) {
      loadingCache.invalidate(user.getId());
    }
  }

  /** Invalidates multiple user entries in the cache. */
  public void invalidate(final Iterable<ExternalUser> users) {
    if (cacheEnabled) {
      loadingCache.invalidateAll(convertExternalUsersToUserIds(users));
    }
  }

  /** Invalidates all user entries in the cache. */
  public void invalidateAll() {
    if (cacheEnabled) {
      loadingCache.invalidateAll();
    }
  }

  protected boolean checkCacheEnabled() {
    return cacheEnabled;
  }

  protected long size() {
    if (cacheEnabled) {
      loadingCache.cleanUp();
      return loadingCache.size();
    } else {
      return -1L;
    }
  }
}
