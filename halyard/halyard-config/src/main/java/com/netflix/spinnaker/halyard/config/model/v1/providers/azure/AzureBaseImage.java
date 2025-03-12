/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.azure;

import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AzureBaseImage
    extends BaseImage<
        AzureBaseImage.AzureOperatingSystemSettings, AzureBaseImage.AzureVirtualizationSettings> {

  private AzureOperatingSystemSettings baseImage;
  private AzureVirtualizationSettings virtualizationSettings;

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class AzureOperatingSystemSettings extends BaseImage.ImageSettings {
    String publisher;
    String offer;
    String sku;
    String version;
  }

  @Data
  public static class AzureVirtualizationSettings {}
}
