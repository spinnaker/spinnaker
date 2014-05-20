/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.bluespar.kork.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.LookupService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EurekaComponents {
    @Bean
    public ApplicationInfoManagerFactoryBean applicationInfoManager(EurekaInstanceConfig eurekaInstanceConfig) {
        return new ApplicationInfoManagerFactoryBean(eurekaInstanceConfig);
    }

    @Bean
    public InstanceInfo instanceInfo(ApplicationInfoManager applicationInfoManager) {
        return applicationInfoManager.getInfo();
    }

    @Bean
    public EurekaInstanceConfig eurekaInstanceConfig(@Value("${eureka.instance.namespace:netflix.appinfo.}") String namespace) {
        return new MyDataCenterInstanceConfig(fixNamespace(namespace));
    }

    @Bean
    public EurekaClientConfig eurekaClientConfig(@Value("${eureka.instance.namespace:netflix.discovery.}") String namespace) {
        return new DefaultEurekaClientConfig(fixNamespace(namespace));
    }

    @Bean
    public LookupService lookupService(Applications applications) {
        return new StaticLookupService(applications);
    }

    @Bean
    public Applications applications(ListableBeanFactory beanFactory) {
        Applications applications = new Applications();
        for (Application application : beanFactory.getBeansOfType(Application.class).values()) {
            applications.addApplication(application);
        }
        applications.shuffleInstances(false);
        return applications;
    }

    private static String fixNamespace(String namespace) {
        return namespace.endsWith(".") ? namespace : namespace + ".";
    }

    private static class ApplicationInfoManagerFactoryBean extends AbstractFactoryBean<ApplicationInfoManager> {
        private final EurekaInstanceConfig config;

        private ApplicationInfoManagerFactoryBean(EurekaInstanceConfig config) {
            this.config = config;
        }

        @Override
        public Class<?> getObjectType() {
            return ApplicationInfoManager.class;
        }

        @Override
        protected ApplicationInfoManager createInstance() throws Exception {
            ApplicationInfoManager.getInstance().initComponent(config);
            return ApplicationInfoManager.getInstance();
        }
    }
}
