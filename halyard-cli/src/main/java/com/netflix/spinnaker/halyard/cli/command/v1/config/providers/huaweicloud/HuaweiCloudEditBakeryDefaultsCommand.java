/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.huaweicloud;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.BakeryCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudBakeryDefaults;

@Parameters(separators = "=")
public class HuaweiCloudEditBakeryDefaultsCommand
    extends AbstractEditBakeryDefaultsCommand<HuaweiCloudBakeryDefaults> {
  protected String getProviderName() {
    return "huaweicloud";
  }

  @Parameter(
      names = "--auth-url",
      required = true,
      description = "Set the default auth URL your images will be baked in.")
  private String authUrl;

  @Parameter(
      names = "--username",
      required = true,
      description = "Set the default username your images will be baked with.")
  private String username;

  @Parameter(
      names = "--password",
      required = true,
      password = true,
      description = "Set the default password your images will be baked with.")
  private String password;

  @Parameter(
      names = "--project-name",
      required = true,
      description = "Set the default project name your images will be baked in.")
  private String projectName;

  @Parameter(
      names = "--domain-name",
      required = true,
      description = "Set the default domainName your images will be baked in.")
  private String domainName;

  @Parameter(
      names = "--insecure",
      required = true,
      arity = 1,
      description = "The security setting (true/false) for connecting to the HuaweiCloud account.")
  private Boolean insecure;

  @Parameter(
      names = "--vpc-id",
      required = true,
      description = "Set the vpc your images will be baked in.")
  private String vpcId;

  @Parameter(
      names = "--subnet-id",
      required = true,
      description = "Set the subnet your images will be baked in.")
  private String subnetId;

  @Parameter(
      names = "--eip-bandwidth-size",
      required = true,
      description = "Set the bandwidth size of eip your images will be baked in.")
  private Integer eipBandwidthSize;

  @Parameter(
      names = "--security-group",
      required = true,
      description = "Set the default security group your images will be baked in.")
  private String securityGroup;

  @Parameter(
      names = "--template-file",
      description = BakeryCommandProperties.TEMPLATE_FILE_DESCRIPTION)
  private String templateFile;

  @Override
  protected BakeryDefaults editBakeryDefaults(HuaweiCloudBakeryDefaults bakeryDefaults) {
    bakeryDefaults.setAuthUrl(isSet(authUrl) ? authUrl : bakeryDefaults.getAuthUrl());
    bakeryDefaults.setUsername(isSet(username) ? username : bakeryDefaults.getUsername());
    bakeryDefaults.setPassword(isSet(password) ? password : bakeryDefaults.getPassword());
    bakeryDefaults.setProjectName(
        isSet(projectName) ? projectName : bakeryDefaults.getProjectName());
    bakeryDefaults.setDomainName(isSet(domainName) ? domainName : bakeryDefaults.getDomainName());
    bakeryDefaults.setInsecure(isSet(insecure) ? insecure : bakeryDefaults.getInsecure());
    bakeryDefaults.setVpcId(isSet(vpcId) ? vpcId : bakeryDefaults.getVpcId());
    bakeryDefaults.setSubnetId(isSet(subnetId) ? subnetId : bakeryDefaults.getSubnetId());
    bakeryDefaults.setEipBandwidthSize(
        isSet(eipBandwidthSize) ? eipBandwidthSize : bakeryDefaults.getEipBandwidthSize());
    bakeryDefaults.setSecurityGroup(
        isSet(securityGroup) ? securityGroup : bakeryDefaults.getSecurityGroup());
    bakeryDefaults.setTemplateFile(
        isSet(templateFile) ? templateFile : bakeryDefaults.getTemplateFile());

    return bakeryDefaults;
  }
}
