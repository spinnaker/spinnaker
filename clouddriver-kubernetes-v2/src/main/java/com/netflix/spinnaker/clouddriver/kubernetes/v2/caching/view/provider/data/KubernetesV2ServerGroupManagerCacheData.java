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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.data;

import com.netflix.spinnaker.cats.cache.CacheData;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KubernetesV2ServerGroupManagerCacheData implements KubernetesV2CacheData {
  private CacheData serverGroupManagerData;
  private List<CacheData> serverGroupData;

  public CacheData primaryData() {
    return serverGroupManagerData;
  }
}
