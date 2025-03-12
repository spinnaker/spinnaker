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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.BakeryCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsBakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsBaseImage;

/** Interact with the aws provider's bakery */
@Parameters(separators = "=")
public class AwsEditBakeryDefaultsCommand
    extends AbstractEditBakeryDefaultsCommand<AwsBakeryDefaults> {
  protected String getProviderName() {
    return "aws";
  }

  @Parameter(
      names = "--aws-access-key",
      description = "The default access key used to communicate with AWS.")
  private String awsAccessKey;

  @Parameter(
      names = "--aws-secret-key",
      description = "The secret key used to communicate with AWS.",
      password = true)
  private String awsSecretKey;

  @Parameter(
      names = "--aws-subnet-id",
      description =
          "If using VPC, the default ID of the subnet, such as subnet-12345def, where Packer will launch "
              + "the EC2 instance. This field is required if you are using a non-default VPC.")
  private String awsSubnetId;

  @Parameter(
      names = "--aws-vpc-id",
      description =
          "If launching into a VPC subnet, Packer needs the VPC ID in order to create a temporary security "
              + "group within the VPC. Requires subnet_id to be set. If this default value is left blank, Packer "
              + "will try to get the VPC ID from the subnet_id.")
  private String awsVpcId;

  @Parameter(
      names = "--aws-associate-public-ip-address",
      description =
          "If using a non-default VPC, public IP addresses are not provided by default. If this is enabled, "
              + "your new instance will get a Public IP.",
      arity = 1)
  private Boolean awsAssociatePublicIpAddress;

  @Parameter(
      names = "--default-virtualization-type",
      description =
          "The default type of virtualization for the AMI you are building. This option must match the "
              + "supported virtualization type of source_ami. Can be pv or hvm.")
  private AwsBaseImage.AwsVirtualizationSettings.VmType defaultVirtualizationType;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  @Override
  protected AwsBakeryDefaults editBakeryDefaults(AwsBakeryDefaults bakeryDefaults) {
    bakeryDefaults.setAwsAccessKey(
        isSet(awsAccessKey) ? awsAccessKey : bakeryDefaults.getAwsAccessKey());
    bakeryDefaults.setAwsSecretKey(
        isSet(awsSecretKey) ? awsSecretKey : bakeryDefaults.getAwsSecretKey());
    bakeryDefaults.setAwsSubnetId(
        isSet(awsSubnetId) ? awsSubnetId : bakeryDefaults.getAwsSubnetId());
    bakeryDefaults.setAwsVpcId(isSet(awsVpcId) ? awsVpcId : bakeryDefaults.getAwsVpcId());
    bakeryDefaults.setAwsAssociatePublicIpAddress(
        isSet(awsAssociatePublicIpAddress)
            ? awsAssociatePublicIpAddress
            : bakeryDefaults.getAwsAssociatePublicIpAddress());
    bakeryDefaults.setDefaultVirtualizationType(
        isSet(defaultVirtualizationType)
            ? defaultVirtualizationType
            : bakeryDefaults.getDefaultVirtualizationType());
    bakeryDefaults.setTemplateFile(
        isSet(templateFile) ? templateFile : bakeryDefaults.getTemplateFile());

    return bakeryDefaults;
  }
}
