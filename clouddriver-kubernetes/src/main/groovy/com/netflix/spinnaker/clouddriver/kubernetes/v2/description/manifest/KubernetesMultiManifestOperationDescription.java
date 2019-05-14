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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@EqualsAndHashCode(callSuper = true)
@Data
public class KubernetesMultiManifestOperationDescription
    extends KubernetesAtomicOperationDescription {
  private String manifestName;
  private String location;
  private List<String> kinds = new ArrayList<>();
  private KubernetesSelectorList labelSelectors = new KubernetesSelectorList();

  @JsonIgnore
  public boolean isDynamic() {
    return StringUtils.isEmpty(manifestName);
  }

  public List<KubernetesCoordinates> getAllCoordinates() {
    return kinds.stream()
        .map(
            k ->
                KubernetesCoordinates.builder()
                    .namespace(location)
                    .kind(KubernetesKind.fromString(k))
                    .build())
        .collect(Collectors.toList());
  }

  @JsonIgnore
  @Deprecated
  public KubernetesCoordinates getPointCoordinates() {
    Pair<KubernetesKind, String> parsedName = KubernetesManifest.fromFullResourceName(manifestName);

    return KubernetesCoordinates.builder()
        .namespace(location)
        .kind(parsedName.getLeft())
        .name(parsedName.getRight())
        .build();
  }
}
