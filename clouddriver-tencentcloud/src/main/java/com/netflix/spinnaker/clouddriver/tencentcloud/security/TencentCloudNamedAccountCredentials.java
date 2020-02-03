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

package com.netflix.spinnaker.clouddriver.tencentcloud.security;

import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.config.TencentCloudConfigurationProperties;
import com.netflix.spinnaker.clouddriver.tencentcloud.model.TencentCloudBasicResource;
import com.netflix.spinnaker.clouddriver.tencentcloud.names.TencentCloudBasicResourceNamer;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.moniker.Namer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
@Data
public class TencentCloudNamedAccountCredentials
    implements AccountCredentials<TencentCloudCredentials> {

  private String name;
  private String environment;
  private String accountType;
  private TencentCloudCredentials credentials;
  private List<TencentCloudRegion> regions;
  private List<String> requiredGroupMembership;
  private Permissions permissions;
  private Namer<TencentCloudBasicResource> namer = new TencentCloudBasicResourceNamer();

  public TencentCloudNamedAccountCredentials(
      TencentCloudConfigurationProperties.ManagedAccount managedAccount) {
    this.name = managedAccount.getName();
    this.environment = managedAccount.getEnvironment();
    this.accountType = managedAccount.getAccountType();
    this.credentials =
        new TencentCloudCredentials(managedAccount.getSecretId(), managedAccount.getSecretKey());
    this.regions = buildRegions(managedAccount.getRegions());
    NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(name)
        .setNamer(TencentCloudBasicResource.class, namer);
  }

  private static List<TencentCloudRegion> buildRegions(List<String> regions) {
    if (CollectionUtils.isEmpty(regions)) {
      return new ArrayList<>();
    } else {
      return regions.stream().map(TencentCloudRegion::new).collect(Collectors.toList());
    }
  }

  @Override
  public String getCloudProvider() {
    return TencentCloudProvider.ID;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  @Getter
  @RequiredArgsConstructor
  public static final class TencentCloudRegion {
    @Nonnull private final String name;
  }
}
