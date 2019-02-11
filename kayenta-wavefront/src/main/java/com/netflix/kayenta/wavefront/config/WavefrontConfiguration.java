/*
 * Copyright 2019 Intuit, Inc.
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
package com.netflix.kayenta.wavefront.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.wavefront.metrics.WavefrontMetricsService;
import com.netflix.kayenta.wavefront.security.WavefrontCredentials;
import com.netflix.kayenta.wavefront.security.WavefrontNamedAccountCredentials;
import com.netflix.kayenta.wavefront.service.WavefrontRemoteService;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import retrofit.converter.JacksonConverter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.wavefront.enabled")
@ComponentScan({"com.netflix.kayenta.wavefront"})
@Slf4j
public class WavefrontConfiguration {
    @Bean
    @ConfigurationProperties("kayenta.wavefront")
    WavefrontConfigurationProperties wavefrontConfigurationProperties() {
        return new WavefrontConfigurationProperties();
    }

    @Bean
    MetricsService WavefrontMetricsService(WavefrontConfigurationProperties wavefrontConfigurationProperties,
                                         RetrofitClientFactory retrofitClientFactory,
                                         ObjectMapper objectMapper,
                                         OkHttpClient okHttpClient,
                                         AccountCredentialsRepository accountCredentialsRepository) throws IOException {
        WavefrontMetricsService.WavefrontMetricsServiceBuilder wavefrontMetricsServiceBuilder = WavefrontMetricsService.builder();

        for (WavefrontManagedAccount wavefrontManagedAccount : wavefrontConfigurationProperties.getAccounts()) {
            String name = wavefrontManagedAccount.getName();
            List<AccountCredentials.Type> supportedTypes = wavefrontManagedAccount.getSupportedTypes();

            log.info("Registering Wavefront account {} with supported types {}.", name, supportedTypes);

            WavefrontCredentials wavefrontCredentials =
                    WavefrontCredentials
                            .builder()
                            .apiToken(wavefrontManagedAccount.getApiToken())
                            .build();
            WavefrontNamedAccountCredentials.WavefrontNamedAccountCredentialsBuilder wavefrontNamedAccountCredentialsBuilder =
                    WavefrontNamedAccountCredentials
                            .builder()
                            .name(name)
                            .endpoint(wavefrontManagedAccount.getEndpoint())
                            .credentials(wavefrontCredentials);

            if (!CollectionUtils.isEmpty(supportedTypes)) {
                if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
                    WavefrontRemoteService wavefrontRemoteService = retrofitClientFactory.createClient(WavefrontRemoteService.class,
                            new JacksonConverter(objectMapper),
                            wavefrontManagedAccount.getEndpoint(),
                            okHttpClient);

                    wavefrontNamedAccountCredentialsBuilder.wavefrontRemoteService(wavefrontRemoteService);
                }

                wavefrontNamedAccountCredentialsBuilder.supportedTypes(supportedTypes);
            }

            WavefrontNamedAccountCredentials wavefrontNamedAccountCredentials = wavefrontNamedAccountCredentialsBuilder.build();
            accountCredentialsRepository.save(name, wavefrontNamedAccountCredentials);
            wavefrontMetricsServiceBuilder.accountName(name);
        }

        WavefrontMetricsService wavefrontMetricsService = wavefrontMetricsServiceBuilder.build();
        log.info("Populated WavefrontMetricsService with {} Wavefront accounts.", wavefrontMetricsService.getAccountNames().size());
        return wavefrontMetricsService;
    }
}
