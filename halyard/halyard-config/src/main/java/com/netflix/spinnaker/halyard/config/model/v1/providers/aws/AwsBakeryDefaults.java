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

import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AwsBakeryDefaults extends BakeryDefaults<AwsBaseImage> {
  private String awsAccessKey;
  @Secret private String awsSecretKey;
  private String awsSubnetId;
  private String awsVpcId;
  private Boolean awsAssociatePublicIpAddress;
  private AwsBaseImage.AwsVirtualizationSettings.VmType defaultVirtualizationType;
}
