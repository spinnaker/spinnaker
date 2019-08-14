/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusUtils;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Common logic for Titus deploy actions. Plus some utility methods for easing the Groovy to Java
 * migration.
 */
abstract class AbstractTitusDeployAction {

  AccountCredentialsRepository accountCredentialsRepository;
  TitusClientProvider titusClientProvider;

  static boolean isNullOrEmpty(String string) {
    return Strings.isNullOrEmpty(string);
  }

  static boolean isNullOrEmpty(List<?> list) {
    return (list == null) || list.isEmpty();
  }

  static boolean isNullOrEmpty(Map<?, ?> map) {
    return (map == null) || map.isEmpty();
  }

  AbstractTitusDeployAction(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.titusClientProvider = titusClientProvider;
  }

  /** Build a Titus client, provided a deployment Source object. */
  @Nullable
  TitusClient buildSourceTitusClient(TitusDeployDescription.Source source) {
    if (!isNullOrEmpty(source.getAccount())
        && !isNullOrEmpty(source.getRegion())
        && !isNullOrEmpty(source.getAsgName())) {
      AccountCredentials sourceCredentials =
          accountCredentialsRepository.getOne(source.getAccount());

      TitusUtils.assertTitusAccountCredentialsType(sourceCredentials);

      return titusClientProvider.getTitusClient(
          (NetflixTitusCredentials) sourceCredentials, source.getRegion());
    }
    return null;
  }
}
