/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccounts;
import com.netflix.spinnaker.igor.helm.accounts.HelmAccountsService;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import retrofit2.Retrofit;

@Configuration
@ConditionalOnProperty("helm.enabled")
@Slf4j
public class HelmConfig {
  @Bean
  HelmAccounts helmAccounts() {
    return new HelmAccounts();
  }

  @Bean
  HelmAccountsService helmAccountsService(
      OkHttp3ClientConfiguration okHttp3ClientConfig,
      IgorConfigurationProperties igorConfigurationProperties) {
    String address = igorConfigurationProperties.getServices().getClouddriver().getBaseUrl();

    if (StringUtils.isEmpty(address)) {
      log.warn(
          "No Clouddriver URL is configured - Igor will be unable to fetch Helm charts and repository indexes");
    }

    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(address))
        .client(okHttp3ClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(new HelmConverterFactory())
        .build()
        .create(HelmAccountsService.class);
  }
}
