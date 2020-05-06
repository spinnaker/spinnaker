/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.springframework.stereotype.Component;

@ParametersAreNonnullByDefault
public class AccountResourcePropertyRegistry implements ResourcePropertyRegistry {
  private final GlobalResourcePropertyRegistry globalResourcePropertyRegistry;
  private final ImmutableMap<KubernetesKind, KubernetesResourceProperties> propertyMap;

  private AccountResourcePropertyRegistry(
      GlobalResourcePropertyRegistry globalResourcePropertyRegistry,
      Collection<KubernetesResourceProperties> resourceProperties) {
    this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
    this.propertyMap =
        resourceProperties.stream()
            .collect(toImmutableMap(p -> p.getHandler().kind(), Function.identity()));
  }

  @Override
  @Nonnull
  public KubernetesResourceProperties get(KubernetesKind kind) {
    KubernetesResourceProperties accountResult = propertyMap.get(kind);
    if (accountResult != null) {
      return accountResult;
    }

    return globalResourcePropertyRegistry.get(kind);
  }

  @Override
  @Nonnull
  public ImmutableCollection<KubernetesResourceProperties> values() {
    return new ImmutableList.Builder<KubernetesResourceProperties>()
        .addAll(globalResourcePropertyRegistry.values())
        .addAll(propertyMap.values())
        .build();
  }

  @Component
  public static class Factory {
    private final GlobalResourcePropertyRegistry globalResourcePropertyRegistry;

    public Factory(GlobalResourcePropertyRegistry globalResourcePropertyRegistry) {
      this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
    }

    public AccountResourcePropertyRegistry create(
        Collection<KubernetesResourceProperties> resourceProperties) {
      return new AccountResourcePropertyRegistry(
          globalResourcePropertyRegistry, resourceProperties);
    }
  }
}
