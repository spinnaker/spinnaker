/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.NoDiscoveryApplicationStatusPublisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent

@Configuration
class DiscoveryPollingConfig {

    @Configuration
    @ConditionalOnMissingBean(DiscoveryClient.class)
    static class NoDiscoveryConfig {
        @Autowired
        ApplicationEventPublisher publisher

        @Value('${spring.application.name:igor}')
        String appName

        @Bean
        ApplicationListener<ContextRefreshedEvent> discoveryStatusPoller() {
            new NoDiscoveryApplicationStatusPublisher(publisher)
        }
    }

    @Configuration
    @ConditionalOnBean(DiscoveryClient.class)
    static class DiscoveryConfig {

        @Bean
        String currentInstanceId(InstanceInfo instanceInfo) {
            instanceInfo.getInstanceId()
        }

    }

}
