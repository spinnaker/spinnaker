package com.netflix.kayenta.clickhouse.config;

import com.netflix.kayenta.clickhouse.metrics.ClickhouseMetricsService;
import com.netflix.kayenta.clickhouse.security.ClickhouseCredentials;
import com.netflix.kayenta.clickhouse.security.ClickhouseNamedAccountCredentials;
import com.netflix.kayenta.clickhouse.service.ClickhouseRemoteService;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

@Configuration
@ConditionalOnProperty("kayenta.clickhouse.enabled")
@ComponentScan({"com.netflix.kayenta.clickhouse"})
@Slf4j
public class ClickhouseConfiguration {

  @Bean
  @ConfigurationProperties("kayenta.clickhouse")
  ClickhouseConfigurationProperties clickhouseConfigurationProperties() {
    return new ClickhouseConfigurationProperties();
  }

  @Bean
  @ConfigurationProperties("kayenta.clickhouse.test-controller-defaults")
  ClickhouseConfigurationTestControllerDefaultProperties
      clickhouseConfigurationTestControllerDefaultProperties() {
    return new ClickhouseConfigurationTestControllerDefaultProperties();
  }

  @Bean
  MetricsService clickhouseMetricsService(
      ClickhouseConfigurationProperties clickhouseConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository) {

    ClickhouseMetricsService.ClickhouseMetricsServiceBuilder metricsServiceBuilder =
        ClickhouseMetricsService.builder();

    for (ClickhouseManagedAccount account : clickhouseConfigurationProperties.getAccounts()) {
      String name = account.getName();
      List<AccountCredentials.Type> supportedTypes = account.getSupportedTypes();

      ClickhouseCredentials credentials =
          ClickhouseCredentials.builder()
              .endpointUrl(account.getEndpointUrl())
              .username(account.getUsername())
              .password(account.getPassword())
              .database(account.getDatabase())
              .build();

      ClickhouseNamedAccountCredentials.ClickhouseNamedAccountCredentialsBuilder
          accountCredentialsBuilder =
              ClickhouseNamedAccountCredentials.builder().name(name).credentials(credentials);

      if (!CollectionUtils.isEmpty(supportedTypes)) {
        if (supportedTypes.contains(AccountCredentials.Type.METRICS_STORE)) {
          accountCredentialsBuilder.clickhouseRemoteService(
              new ClickhouseRemoteService(credentials));
        }
        accountCredentialsBuilder.supportedTypes(supportedTypes);
      }

      accountCredentialsRepository.save(name, accountCredentialsBuilder.build());
      metricsServiceBuilder.accountName(name);
    }

    log.info(
        "Configured the Clickhouse Metrics Service with the following accounts: {}",
        clickhouseConfigurationProperties.getAccounts().stream()
            .map(ClickhouseManagedAccount::getName)
            .collect(Collectors.joining(",")));

    return metricsServiceBuilder.build();
  }
}
