/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.config;

import com.google.common.collect.Sets;
import com.netflix.spinnaker.fiat.model.ServiceAccount;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider;
import lombok.Data;
import lombok.val;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class ServiceAccountsConfig {

  @Bean
  public ServiceAccountList serviceAccountList() {
    return new ServiceAccountList();
  }

  @Bean
  public ServiceAccountProvider serviceAccountProvider(ServiceAccountList serviceAccountList,
                                                       PermissionsRepository repo) {
    val serviceAcctsByName = serviceAccountList
        .getServiceAccounts()
        .stream()
        .map(serviceAccount -> {
          // Can't resolve full account/application permissions here because it would cause
          // a dependency cycle. Instead, account/applications are resolved on the first
          // periodic sync.
          repo.put(new UserPermission()
                       .setId(serviceAccount.getName())
                       .setServiceAccounts(Sets.newHashSet(serviceAccount)));
          return serviceAccount;
        })
        .collect(Collectors.toMap(ServiceAccount::getName, Function.identity()));

    return new ServiceAccountProvider().setServiceAccountsByName(serviceAcctsByName);
  }

  // It's silly that we have to define a container class for List objects, but that's apparently
  // how Spring @ConfigurationProperties works.
  //
  // TODO(ttomsu): It's likely that users will need/want to create service accounts within the
  // application. Therefore, it's quite possible this will all go away in favor of the file watcher/
  // bucket watcher implementation.
  @ConfigurationProperties("auth")
  @Data
  public static class ServiceAccountList {
    private List<ServiceAccount> serviceAccounts;
  }
}
