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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
@NonnullByDefault
public final class KubernetesManifestTraffic {
  private final ImmutableList<String> loadBalancers;

  @ParametersAreNullableByDefault
  public KubernetesManifestTraffic(List<String> loadBalancers) {
    this.loadBalancers =
        Optional.ofNullable(loadBalancers).map(ImmutableList::copyOf).orElseGet(ImmutableList::of);
  }
}
