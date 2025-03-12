/*
 * Copyright 2022 Armory, Inc
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
 */

package com.netflix.spinnaker.fiat.roles;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

public interface UserRolesSyncStrategy {
  long syncAndReturn(List<String> roles);

  long syncServiceAccount(String serviceAccountId, List<String> roles);

  @RequiredArgsConstructor
  class DefaultSynchronizationStrategy implements UserRolesSyncStrategy {

    private final Synchronizer synchronizer;

    @Override
    public long syncAndReturn(List<String> roles) {
      return this.synchronizer.syncAndReturn(roles);
    }

    @Override
    public long syncServiceAccount(String serviceAccountId, List<String> roles) {
      return this.synchronizer.syncServiceAccount(serviceAccountId, roles);
    }
  }

  @Slf4j
  class CachedSynchronizationStrategy implements UserRolesSyncStrategy {

    private final Synchronizer synchronizer;
    private final CallableCache<List<String>, Long> callableCache;

    public CachedSynchronizationStrategy(@NonNull Synchronizer synchronizer) {
      this.synchronizer = synchronizer;
      this.callableCache = new CallableCache<>();
    }

    public CachedSynchronizationStrategy(
        @NonNull Synchronizer synchronizer, @NonNull CallableCache<List<String>, Long> cache) {
      this.synchronizer = synchronizer;
      this.callableCache = cache;
    }

    @Override
    public long syncAndReturn(List<String> roles) {
      final List<String> nonNullRoles = (roles != null) ? roles : new ArrayList<>();
      try {
        return this.callableCache
            .runAndGetResult(nonNullRoles, () -> this.synchronizer.syncAndReturn(nonNullRoles))
            .get();
      } catch (Exception e) {
        log.error(e.getMessage());
        throw new RolesSynchronizationException();
      } finally {
        this.callableCache.clear(nonNullRoles);
      }
    }

    @Override
    public long syncServiceAccount(String serviceAccountId, List<String> roles) {
      return this.synchronizer.syncServiceAccount(serviceAccountId, roles);
    }
  }

  final class RolesSynchronizationException extends RuntimeException {
    RolesSynchronizationException() {
      super("Unexpected exception occurred while running roles synchronization");
    }
  }
}
