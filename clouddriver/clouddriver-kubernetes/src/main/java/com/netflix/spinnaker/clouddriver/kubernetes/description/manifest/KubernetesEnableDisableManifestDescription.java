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
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public final class KubernetesEnableDisableManifestDescription
    extends KubernetesManifestOperationDescription {
  private int targetPercentage = 100;
  // optional: can be inferred from the annotations as well
  @Nonnull private ImmutableList<String> loadBalancers = ImmutableList.of();

  @Nonnull
  public KubernetesEnableDisableManifestDescription setLoadBalancers(
      @Nullable List<String> loadBalancers) {
    this.loadBalancers =
        Optional.ofNullable(loadBalancers).map(ImmutableList::copyOf).orElseGet(ImmutableList::of);
    return this;
  }
}
