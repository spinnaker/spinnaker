/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.config
import com.netflix.spinnaker.clouddriver.cf.deploy.handlers.CloudFoundryDeployHandler
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryCredentialsInitializer
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.utils.DefaultCloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.utils.DefaultRestTemplateFactory
import com.netflix.spinnaker.clouddriver.cf.utils.DefaultS3ServiceFactory
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
/**
 * Configuration for Cloud Foundry provider.
 */
@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('cf.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.cf"])
class CloudFoundryConfig {

	@Bean
	CloudFoundryConfigurationProperties cfConfigurationProperties() {
		new CloudFoundryConfigurationProperties();
	}

	@Bean
	CloudFoundryCredentialsInitializer cloudFoundryCredentialsInitializer() {
		new CloudFoundryCredentialsInitializer();
	}

	@Bean
	@ConditionalOnMissingBean(CloudFoundryClientFactory)
	CloudFoundryClientFactory cloudFoundryClientFactory() {
		new DefaultCloudFoundryClientFactory()
	}

	@Bean
	@ConditionalOnMissingBean(CloudFoundryDeployHandler)
	CloudFoundryDeployHandler cloudFoundryDeployHandler(CloudFoundryClientFactory clientFactory) {
		new CloudFoundryDeployHandler(
			clientFactory					: clientFactory,
			restTemplateFactory		: new DefaultRestTemplateFactory(),
			s3ServiceFactory: new DefaultS3ServiceFactory()
		)
	}

	@Bean
	OperationPoller cloudFoundryOperationPoller(CloudFoundryConfigurationProperties properties) {
		new OperationPoller(
				properties.asyncOperationTimeoutSecondsDefault,
				properties.asyncOperationMaxPollingIntervalSeconds
		)
	}

}
