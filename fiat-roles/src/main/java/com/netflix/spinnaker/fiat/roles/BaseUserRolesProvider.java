/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.fiat.roles;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.netflix.spinnaker.fiat.config.UserRolesProviderCacheConfig;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** UserRolesProvider base implementation class providing caching for user roles. */
public abstract class BaseUserRolesProvider implements UserRolesProvider {

  /** Cache loader implementation consistent with UserRolesProvider. */
  class BaseUserRolesProviderCacheLoader extends CacheLoader<String, Collection<Role>> {
    @Override
    public Collection<Role> load(final @NotNull String userId) {
      return loadRolesForUser(new ExternalUser().setId(userId));
    }

    @Override
    public Map<String, Collection<Role>> loadAll(@NotNull Iterable<? extends String> userIds) {
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

    // Build the cache.
    this.loadingCache = enableCache ? buildCache(expireAfterWriteSeconds) : null;
    // Expose the cache for usage.
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
        // Responses by default stored as a collection in the cache to support the UserRolesProvider
        // interface.
        // If already a list, avoid duplicate list creation, else, return a new list.
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

  /**
   * Helper method to encapsulate shared logic for converting list of users to ids for interacting
   * with the cache.
   *
   * @param users Users to convert to ids.
   * @return List of ids, one per user.
   */
  private List<String> convertExternalUsersToUserIds(final Iterable<ExternalUser> users) {
    return StreamSupport.stream(users.spliterator(), false)
        .map(ExternalUser::getId)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(final Collection<ExternalUser> users) {
    try {
      if (cacheEnabled) {
        // Convert the cached response to a mutable map object.
        // By default, Guava cache getAll() responses are returned as an immutable map.
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

  /**
   * Loads roles for a single user to the cache. This method must be implemented in order to support
   * cache loading functionality.
   *
   * @param user User for which to load roles.
   * @return List of roles for the user.
   * @throws ProviderException if there is an exception loading roles.
   */
  protected abstract List<Role> loadRolesForUser(final ExternalUser user) throws ProviderException;

  /**
   * Loads roles for multiple users to the cache. This method provides a default bulk cache load
   * implementation which simply loads the cache one user entry at a time using {@link
   * #loadRolesForUser}. This method should be overridden when bulk retrieval is significantly more
   * efficient than many individual lookups.
   *
   * @param users Users for which to load roles.
   * @return Mapping of user id to roles.
   * @throws ProviderException if there is an exception loading roles.
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
}
