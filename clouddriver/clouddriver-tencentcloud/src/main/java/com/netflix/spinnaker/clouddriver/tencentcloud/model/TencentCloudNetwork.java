/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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
 */

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.netflix.spinnaker.clouddriver.model.Network;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import lombok.Data;

@Data
public class TencentCloudNetwork implements Network {

  private String id;
  private String name;
  private String account;
  private String region;
  private String cidrBlock;
  private Boolean isDefault;

  public TencentCloudNetwork(
      String id, String name, String account, String region, String cidrBlock, Boolean isDefault) {
    this.id = id;
    this.name = name;
    this.account = account;
    this.region = region;
    this.cidrBlock = cidrBlock;
    this.isDefault = isDefault;
  }

  @Override
  public String getCloudProvider() {
    return TencentCloudProvider.ID;
  }

  @Override
  public String getId() {
    return id;
  }
}
