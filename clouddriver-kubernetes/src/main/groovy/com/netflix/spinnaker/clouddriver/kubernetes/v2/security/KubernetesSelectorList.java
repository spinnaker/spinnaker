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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class KubernetesSelectorList {
  private final List<KubernetesSelector> selectors = new ArrayList<>();

  public KubernetesSelectorList() { }

  public KubernetesSelectorList(KubernetesSelector... selectors) {
    this.selectors.addAll(Arrays.asList(selectors));
  }

  public KubernetesSelectorList addSelector(KubernetesSelector selector) {
    selectors.add(selector);
    return this;
  }

  public boolean isEmpty() {
    return selectors.isEmpty();
  }

  @Override
  public String toString() {
    return String.join(",", selectors.stream().map(KubernetesSelector::toString).collect(Collectors.toList()));
  }
}
