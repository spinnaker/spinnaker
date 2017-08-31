package com.netflix.kayenta.judge.config;

import com.netflix.kayenta.metrics.MapBackedMetricsServiceRepository;
import com.netflix.kayenta.metrics.MetricSetMixerService;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.netflix.kayenta.storage.MapBackedStorageServiceRepository;
import com.netflix.kayenta.storage.StorageServiceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({
        "com.netflix.kayenta.judge",
        "com.netflix.kayenta.judge.config"
})

public class NetflixJudgeConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccountCredentialsRepository.class)
    AccountCredentialsRepository accountCredentialsRepository() {
        return new MapBackedAccountCredentialsRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MetricsServiceRepository.class)
    MetricsServiceRepository metricsServiceRepository() {
        return new MapBackedMetricsServiceRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    MetricSetMixerService metricSetMixerService() {
        return new MetricSetMixerService();
    }

    @Bean
    @ConditionalOnMissingBean(StorageServiceRepository.class)
    StorageServiceRepository storageServiceRepository() {
        return new MapBackedStorageServiceRepository();
    }
}
