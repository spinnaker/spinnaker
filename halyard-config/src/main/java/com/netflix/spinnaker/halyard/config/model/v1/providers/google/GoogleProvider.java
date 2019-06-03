/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.google;

import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GoogleProvider extends HasImageProvider<GoogleAccount, GoogleBakeryDefaults>
    implements Cloneable {
  private List<String> defaultRegions;

  @Override
  public ProviderType providerType() {
    return ProviderType.GOOGLE;
  }

  @Override
  public GoogleBakeryDefaults emptyBakeryDefaults() {
    GoogleBakeryDefaults result = new GoogleBakeryDefaults();
    result.setNetwork("default");
    result.setZone("us-central1-f");
    result.setUseInternalIp(false);
    result.setTemplateFile("gce.json");
    return result;
  }
}
