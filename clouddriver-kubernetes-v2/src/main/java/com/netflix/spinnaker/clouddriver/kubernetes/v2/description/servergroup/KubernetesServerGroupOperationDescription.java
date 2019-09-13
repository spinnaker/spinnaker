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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.servergroup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

@EqualsAndHashCode(callSuper = true)
@Data
public class KubernetesServerGroupOperationDescription
    extends KubernetesAtomicOperationDescription {
  private String serverGroupName;
  private String region; // :(

  @JsonIgnore
  public KubernetesCoordinates getCoordinates() {
    Pair<KubernetesKind, String> parsedName =
        KubernetesManifest.fromFullResourceName(serverGroupName);

    return KubernetesCoordinates.builder()
        .namespace(region)
        .kind(parsedName.getLeft())
        .name(parsedName.getRight())
        .build();
  }
}
