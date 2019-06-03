/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.aws;

import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
public class AwsBaseImage
    extends BaseImage<AwsBaseImage.AwsImageSettings, List<AwsBaseImage.AwsVirtualizationSettings>> {
  private AwsImageSettings baseImage;
  private List<AwsVirtualizationSettings> virtualizationSettings;

  @EqualsAndHashCode(callSuper = true)
  @Data
  @ToString(callSuper = true)
  public static class AwsImageSettings extends BaseImage.ImageSettings {}

  @Data
  public static class AwsVirtualizationSettings {
    String region;
    VmType virtualizationType;
    String instanceType;
    String sourceAmi;
    String sshUserName;
    String winRmUserName;
    String spotPrice;
    String spotPriceAutoProduct;

    public enum VmType {
      pv,
      hvm
    }
  }
}
