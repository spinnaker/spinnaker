package com.netflix.spinnaker.kato.aws.security

import com.amazonaws.auth.AWSCredentialsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties
class BastionConfig {

    @Bean
    @ConfigurationProperties("bastion")
    BastionProperties bastionConfiguration() {
        new BastionProperties()
    }

    @Bean
    @ConditionalOnProperty('bastion.enabled')
    AWSCredentialsProvider bastionCredentialsProvider(BastionProperties bastionConfiguration) {
        def provider = new BastionCredentialsProvider(bastionConfiguration.user, bastionConfiguration.host, bastionConfiguration.port, bastionConfiguration.proxyCluster,
                bastionConfiguration.proxyRegion, bastionConfiguration.accountIamRole)

        provider.refresh()

        provider
    }
}
