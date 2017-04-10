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

import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import lombok.Data;
import lombok.NonNull;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class BaseProvider<R extends Resource.AccessControlled> implements ResourceProvider<R> {

  @Value("${unhealthy.threshold:5}")
  @Setter
  private int unhealthyThreshold;

  private AtomicInteger failureCountSinceLastSuccess = new AtomicInteger(-1);

  void failure() {
    // Increment the failure count only if there has been at least 1 success() call. Otherwise,
    // leave the default, which is considered unhealthy.
    if (!failureCountSinceLastSuccess.compareAndSet(-1, -1)) {
      failureCountSinceLastSuccess.incrementAndGet();
    }
  }

  void success() {
    failureCountSinceLastSuccess.set(0);
  }

  public boolean isProviderHealthy() {
    int count = failureCountSinceLastSuccess.get();
    return 0 <= count && count < unhealthyThreshold;
  }

  public HealthView getHealthView() {
    return new HealthView();
  }

  @Data
  class HealthView {
    boolean providerHealthy = BaseProvider.this.isProviderHealthy();
    int failureCountSinceLastSuccess = BaseProvider.this.failureCountSinceLastSuccess.get();
  }


  @Override
  @SuppressWarnings("unchecked")
  public Set<R> getAllRestricted(@NonNull Set<Role> roles) throws ProviderException {
    val groupNames = roles.stream().map(Role::getName).collect(Collectors.toList());
    return (Set<R>) getAll()
        .stream()
        .filter(resource -> resource.getPermissions().isRestricted())
        .filter(resource -> resource.getPermissions().isAuthorized(roles))
        .collect(Collectors.toSet());

  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<R> getAllUnrestricted() throws ProviderException {
    return (Set<R>) getAll()
        .stream()
        .filter(resource -> !resource.getPermissions().isRestricted())
        .collect(Collectors.toSet());
  }
}
