/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;

@NonnullByDefault
@Value
public class KubernetesServerGroupCacheData implements KubernetesCacheData {
  private final CacheData serverGroupData;
  private final Collection<CacheData> instanceData;
  private final Collection<String> loadBalancerKeys;
  private final Collection<String> serverGroupManagerKeys;

  @Builder
  @ParametersAreNullableByDefault
  private KubernetesServerGroupCacheData(
      @Nonnull CacheData serverGroupData,
      Collection<CacheData> instanceData,
      Collection<String> loadBalancerKeys,
      Collection<String> serverGroupManagerKeys) {
    this.serverGroupData = Objects.requireNonNull(serverGroupData);
    this.instanceData = Optional.ofNullable(instanceData).orElseGet(ImmutableList::of);
    this.loadBalancerKeys = Optional.ofNullable(loadBalancerKeys).orElseGet(ImmutableList::of);
    this.serverGroupManagerKeys =
        Optional.ofNullable(serverGroupManagerKeys).orElseGet(ImmutableList::of);
  }

  @Override
  public CacheData primaryData() {
    return serverGroupData;
  }
}
