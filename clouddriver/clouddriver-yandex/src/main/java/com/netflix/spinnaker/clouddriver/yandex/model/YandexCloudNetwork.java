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

import com.netflix.spinnaker.clouddriver.model.Network;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yandex.cloud.api.vpc.v1.NetworkOuterClass;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class YandexCloudNetwork implements Network {
  private String id;
  private String cloudProvider;
  private String name;
  private String account;
  private String region;

  public static YandexCloudNetwork createFromProto(
      NetworkOuterClass.Network network, String accountName) {
    return YandexCloudNetwork.builder()
        .id(network.getId())
        .cloudProvider(YandexCloudProvider.ID)
        .name(network.getName())
        .account(accountName)
        .region(YandexCloudProvider.REGION)
        .build();
  }
}
