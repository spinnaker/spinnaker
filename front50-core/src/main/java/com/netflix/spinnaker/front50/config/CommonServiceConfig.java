/*
 * Copyright 2020 Adevinta
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

package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.front50.ServiceAccountsService;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonServiceConfig {

  @Bean
  @ConditionalOnBean(ServiceAccountDAO.class)
  ServiceAccountsService serviceAccountsService(
      ServiceAccountDAO serviceAccountDAO,
      Optional<FiatService> fiatService,
      FiatClientConfigurationProperties fiatClientConfigurationProperties,
      FiatConfigurationProperties fiatConfigurationProperties,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    return new ServiceAccountsService(
        serviceAccountDAO,
        fiatService,
        fiatClientConfigurationProperties,
        fiatConfigurationProperties,
        fiatPermissionEvaluator);
  }
}
