/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.huaweicloud.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.huawei.openstack4j.model.compute.ext.AvailabilityZone;
import com.netflix.spinnaker.clouddriver.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.clouddriver.huaweicloud.client.HuaweiCloudClient;
import com.netflix.spinnaker.clouddriver.huaweicloud.client.HuaweiCloudClientImpl;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class HuaweiCloudNamedAccountCredentials
    extends AbstractAccountCredentials<HuaweiCloudCredentials> {

  private final String name;
  private final String environment;
  private final String accountType;
  private final List<String> regions;
  private final Map<String, List<String>> regionToZones;
  private final HuaweiCloudCredentials credentials;
  @JsonIgnore private final HuaweiCloudClient cloudClient;

  public HuaweiCloudNamedAccountCredentials(
      String name,
      String environment,
      String accountType,
      String authUrl,
      String username,
      String password,
      String projectName,
      String domainName,
      Boolean insecure,
      List<String> regions) {
    this.name = name;
    this.environment = environment;
    this.accountType = accountType;
    this.regions = regions;
    this.credentials =
        new HuaweiCloudCredentials(authUrl, username, password, projectName, domainName, insecure);
    this.cloudClient = new HuaweiCloudClientImpl(this.credentials);

    this.regionToZones = new HashMap();
    regions.forEach(
        region -> {
          List<String> result = this.getZonesOfRegion(region);
          if (!result.isEmpty()) {
            regionToZones.put(region, result);
          }
        });
  }

  @Override
  public String getCloudProvider() {
    return HuaweiCloudProvider.ID;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return new ArrayList();
  }

  private List<String> getZonesOfRegion(String region) {
    List<? extends AvailabilityZone> zones = cloudClient.getZones(region);
    return zones.stream()
        .filter(zone -> zone.getZoneState().getAvailable())
        .map(zone -> zone.getZoneName())
        .collect(Collectors.toList());
  }
}
