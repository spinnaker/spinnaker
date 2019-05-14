/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.names;

import com.netflix.spinnaker.moniker.Namer;
import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * The idea is each provider can register (per-account) based on config naming strategy. This
 * assigns a `moniker` to any named resource which is then pushed through the rest of Spinnaker and
 * can be handled without prior knowledge of what naming strategy was used. This is the only place
 * the mapping from (provider, account, resource) -&lt; namer must happen within Spinnaker.
 */
public class NamerRegistry {
  private final List<NamingStrategy> namingStrategies;
  private static Namer defaultNamer = new FriggaReflectiveNamer();
  private static ProviderLookup providerLookup = new ProviderLookup();

  public static Namer getDefaultNamer() {
    return defaultNamer;
  }

  public static ProviderLookup lookup() {
    return providerLookup;
  }

  public NamerRegistry(List<NamingStrategy> namingStrategies) {
    this.namingStrategies = namingStrategies;
  }

  public Namer getNamingStrategy(String strategyName) {
    return this.namingStrategies.stream()
        .filter(strategy -> strategy.getName().equalsIgnoreCase(strategyName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Could not find naming strategy '" + strategyName + "'"));
  }

  @Slf4j
  public static class ResourceLookup {
    private ConcurrentHashMap<Class, Namer> map = new ConcurrentHashMap<>();

    public Namer withResource(Class resource) {
      if (!map.containsKey(resource)) {
        log.debug("Looking up a namer for a non-registered resource");
        return getDefaultNamer();
      } else {
        return map.get(resource);
      }
    }

    public void setNamer(Class resource, Namer namer) {
      map.put(resource, namer);
    }
  }

  @Slf4j
  public static class AccountLookup {
    private ConcurrentHashMap<String, ResourceLookup> map = new ConcurrentHashMap<>();

    public ResourceLookup withAccount(String accountName) {
      if (!map.containsKey(accountName)) {
        log.debug("Looking up a namer for a non-registered account");
        ResourceLookup result = new ResourceLookup();
        map.put(accountName, result);
        return result;
      } else {
        return map.get(accountName);
      }
    }
  }

  @Slf4j
  public static class ProviderLookup {
    private ConcurrentHashMap<String, AccountLookup> map = new ConcurrentHashMap<>();

    public AccountLookup withProvider(String providerName) {
      if (!map.containsKey(providerName)) {
        log.debug("Looking up a namer for a non-registered provider");
        AccountLookup result = new AccountLookup();
        map.put(providerName, result);
        return result;
      } else {
        return map.get(providerName);
      }
    }
  }
}
