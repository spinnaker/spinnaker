/*
 * Copyright 2018 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.MatchExpression;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class KubernetesManifestSelector {
  private Map<String, String> matchLabels = new HashMap<>();
  private List<MatchExpression> matchExpressions = new ArrayList<>();

  @JsonIgnore
  public KubernetesSelectorList toSelectorList() {
    KubernetesSelectorList list = KubernetesSelectorList.fromMatchLabels(matchLabels);
    list.addSelectors(KubernetesSelectorList.fromMatchExpressions(matchExpressions));

    return list;
  }
}
