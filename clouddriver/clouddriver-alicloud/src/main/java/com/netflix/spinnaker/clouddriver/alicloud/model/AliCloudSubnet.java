/*
 * Copyright 2019 Alibaba Group.
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
 */

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.model.Subnet;
import lombok.Data;

@Data
public class AliCloudSubnet implements Subnet {

  private String account;

  private String region;

  private String status;

  private String vSwitchId;

  private String vSwitchName;

  private String vpcId;

  private String zoneId;

  private String type;

  public AliCloudSubnet() {}

  public AliCloudSubnet(
      String account,
      String region,
      String status,
      String vSwitchId,
      String vSwitchName,
      String vpcId,
      String zoneId,
      String type) {
    this.account = account;
    this.region = region;
    this.status = status;
    this.vSwitchId = vSwitchId;
    this.vSwitchName = vSwitchName;
    this.vpcId = vpcId;
    this.zoneId = zoneId;
    this.type = type;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getId() {
    return vpcId;
  }

  @Override
  public String getPurpose() {
    return vSwitchId;
  }
}
