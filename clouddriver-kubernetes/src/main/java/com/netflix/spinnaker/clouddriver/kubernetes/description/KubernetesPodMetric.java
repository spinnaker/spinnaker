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

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.*;

@NonnullByDefault
@Value
public class KubernetesPodMetric {
  private final String podName;
  private final String namespace;
  private final ImmutableList<ContainerMetric> containerMetrics;

  @Builder
  @ParametersAreNullableByDefault
  public KubernetesPodMetric(
      String podName, String namespace, Iterable<ContainerMetric> containerMetrics) {
    this.podName = Strings.nullToEmpty(podName);
    this.namespace = Strings.nullToEmpty(namespace);
    this.containerMetrics =
        Optional.ofNullable(containerMetrics)
            .map(ImmutableList::copyOf)
            .orElseGet(ImmutableList::of);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Value
  public static class ContainerMetric {
    private final String containerName;
    private final ImmutableMap<String, String> metrics;

    @JsonCreator
    @ParametersAreNullableByDefault
    public ContainerMetric(
        @JsonProperty("containerName") String containerName,
        @JsonProperty("metrics") Map<String, String> metrics) {
      this.containerName = Strings.nullToEmpty(containerName);
      this.metrics =
          Optional.ofNullable(metrics).map(ImmutableMap::copyOf).orElseGet(ImmutableMap::of);
    }
  }
}
