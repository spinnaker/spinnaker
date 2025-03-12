/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.model;

import com.netflix.spinnaker.clouddriver.model.Subnet;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yandex.cloud.api.vpc.v1.SubnetOuterClass;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class YandexCloudSubnet implements Subnet {
  private String id;
  private String name;
  private String account;
  private String type;
  private String purpose;
  private String availabilityZone;
  private String vpcId;

  public static YandexCloudSubnet createFromProto(
      SubnetOuterClass.Subnet subnet, String accountName) {
    return YandexCloudSubnet.builder()
        .id(subnet.getId())
        .account(accountName)
        .name(subnet.getName())
        .type(YandexCloudProvider.ID)
        .purpose("internal")
        .availabilityZone(subnet.getZoneId())
        .vpcId(subnet.getNetworkId())
        .build();
  }
}
