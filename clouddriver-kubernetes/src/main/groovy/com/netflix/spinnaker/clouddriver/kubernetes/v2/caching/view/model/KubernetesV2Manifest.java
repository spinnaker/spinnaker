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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KubernetesV2Manifest implements Manifest {
  private final String account;
  private final String name;
  private final String location;
  private final Moniker moniker;
  private final KubernetesManifest manifest;
  private final Status status;
  @Builder.Default private final Set<Artifact> artifacts = new HashSet<>();
  @Builder.Default private final List<KubernetesManifest> events = new ArrayList<>();
  @Builder.Default private final List<Warning> warnings = new ArrayList<>();

  @Builder.Default
  private final List<KubernetesPodMetric.ContainerMetric> metrics = new ArrayList<>();
}
