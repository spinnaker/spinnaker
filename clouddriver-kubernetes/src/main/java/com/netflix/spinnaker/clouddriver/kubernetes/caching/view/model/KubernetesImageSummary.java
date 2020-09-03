/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.ImageSummary;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;

@NonnullByDefault
final class KubernetesImageSummary implements ImageSummary {
  @Getter private final String serverGroupName;
  @Getter private final ImmutableMap<String, Object> buildInfo;

  @Builder
  KubernetesImageSummary(
      String serverGroupName, Map<String, ? extends ImmutableCollection<String>> buildInfo) {
    this.serverGroupName = serverGroupName;
    this.buildInfo = ImmutableMap.copyOf(buildInfo);
  }

  @Nullable
  @Override
  public String getImageId() {
    return null;
  }

  @Nullable
  @Override
  public String getImageName() {
    return null;
  }

  @Nullable
  @Override
  public Map<String, Object> getImage() {
    return null;
  }
}
