/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.model.ServerGroupSummary;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public final class KubernetesServerGroupSummary implements ServerGroupSummary {
  private final String name;
  private final String account;
  private final String namespace;
  private final Moniker moniker;

  public String getRegion() {
    return namespace;
  }
}
