/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

public class AccountResourcePropertyRegistry implements ResourcePropertyRegistry {
  private final GlobalResourcePropertyRegistry globalResourcePropertyRegistry;

  private final ConcurrentHashMap<KubernetesKind, KubernetesResourceProperties> propertyMap =
      new ConcurrentHashMap<>();

  private AccountResourcePropertyRegistry(
      GlobalResourcePropertyRegistry globalResourcePropertyRegistry) {
    this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
  }

  @Nonnull
  public KubernetesResourceProperties get(KubernetesKind kind) {
    KubernetesResourceProperties accountResult = propertyMap.get(kind);
    if (accountResult != null) {
      return accountResult;
    }

    return globalResourcePropertyRegistry.get(kind);
  }

  public void register(KubernetesResourceProperties properties) {
    propertyMap.put(properties.getHandler().kind(), properties);
  }

  public Collection<KubernetesResourceProperties> values() {
    Collection<KubernetesResourceProperties> result =
        new ArrayList<>(globalResourcePropertyRegistry.values());
    result.addAll(propertyMap.values());

    return result;
  }

  @Component
  public static class Factory {
    private final GlobalResourcePropertyRegistry globalResourcePropertyRegistry;

    public Factory(GlobalResourcePropertyRegistry globalResourcePropertyRegistry) {
      this.globalResourcePropertyRegistry = globalResourcePropertyRegistry;
    }

    public AccountResourcePropertyRegistry create() {
      return new AccountResourcePropertyRegistry(globalResourcePropertyRegistry);
    }
  }
}
