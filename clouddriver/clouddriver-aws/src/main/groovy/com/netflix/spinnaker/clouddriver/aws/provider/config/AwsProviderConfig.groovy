/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.config

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.ReservationReport
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(ReservationReportConfigurationProperties)
class AwsProviderConfig {
  @Bean
  AwsProvider awsProvider(CredentialsRepository<NetflixAmazonCredentials> accountCredentialsRepository) {
      return new AwsProvider(accountCredentialsRepository)
  }

  @Bean
  @ConditionalOnProperty("reports.reservation.enabled")
  ExecutorService reservationReportPool(ReservationReportConfigurationProperties reservationReportConfigurationProperties) {
    return Executors.newFixedThreadPool(
        reservationReportConfigurationProperties.threadPoolSize,
        new ThreadFactoryBuilder()
          .setNameFormat(ReservationReport.class.getSimpleName() + "-%d")
          .build());
  }
}
