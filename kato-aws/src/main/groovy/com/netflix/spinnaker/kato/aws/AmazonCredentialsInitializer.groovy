package com.netflix.spinnaker.kato.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.amos.aws.config.CredentialsConfig
import com.netflix.spinnaker.amos.aws.config.CredentialsLoader
import com.netflix.spinnaker.kato.aws.deploy.handlers.BasicAmazonDeployHandler
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.config.KatoAWSConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.AbstractEnvironment
import org.springframework.core.env.MapPropertySource

import static com.amazonaws.regions.Regions.*

@EnableConfigurationProperties
@Configuration
class AmazonCredentialsInitializer {
    @Bean
    @ConfigurationProperties("aws")
    CredentialsConfig credentialsConfig() {
        new CredentialsConfig()
    }

    @Bean
    Class<? extends NetflixAmazonCredentials> credentialsType(CredentialsConfig credentialsConfig) {
        if (!credentialsConfig.accounts) {
            NetflixAmazonCredentials
        } else {
            NetflixAssumeRoleAmazonCredentials
        }
    }

    @Bean
    CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader(AWSCredentialsProvider awsCredentialsProvider, AbstractEnvironment environment, Class<? extends NetflixAmazonCredentials> credentialsType) {
        Map<String, String> envProps = environment.getPropertySources().findAll {
            it instanceof MapPropertySource
        }.collect { MapPropertySource mps ->
            mps.propertyNames.collect {
                [(it): environment.getConversionService().convert(mps.getProperty(it), String)]
            }
        }.flatten().collectEntries()
        new CredentialsLoader<? extends NetflixAmazonCredentials>(awsCredentialsProvider, credentialsType, envProps)
    }

    @Bean
    List<? extends NetflixAmazonCredentials> netflixAmazonCredentials(CredentialsLoader<? extends NetflixAmazonCredentials> credentialsLoader,
                                                                      CredentialsConfig credentialsConfig,
                                                                      AccountCredentialsRepository accountCredentialsRepository,
                                                                      @Value('${default.account.env:default}') String defaultEnv) {

        if (!credentialsConfig.accounts) {
            credentialsConfig.accounts = [new CredentialsConfig.Account(name: defaultEnv)]
            if (!credentialsConfig.defaultRegions) {
                credentialsConfig.defaultRegions = [US_EAST_1, US_WEST_1, US_WEST_2, EU_WEST_1].collect {
                    new CredentialsConfig.Region(name: it.name)
                }
            }
        }

        List<? extends NetflixAmazonCredentials> accounts = credentialsLoader.load(credentialsConfig)

        for (act in accounts) {
            accountCredentialsRepository.save(act.name, act)
        }

        accounts
    }

    @Bean
    @DependsOn('netflixAmazonCredentials')
    BasicAmazonDeployHandler basicAmazonDeployHandler(RegionScopedProviderFactory regionScopedProviderFactory,
                                                      AccountCredentialsRepository accountCredentialsRepository,
                                                      KatoAWSConfig.DeployDefaults deployDefaults) {
        new BasicAmazonDeployHandler(regionScopedProviderFactory, accountCredentialsRepository, deployDefaults)
    }
}
