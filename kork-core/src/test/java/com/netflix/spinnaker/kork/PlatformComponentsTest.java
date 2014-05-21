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

package com.netflix.spinnaker.kork;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.LookupService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlatformComponentsTest {

    @BeforeClass
    public static void init() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        System.setProperty("cassandra.embedded", "false");
    }

    @AfterClass
    public static void cleanup() {
        System.clearProperty("cassandra.embedded");
    }

    @Test
    public void basicContextCreation() {
        try (ConfigurableApplicationContext ctx = createContext()) {
            Assert.assertNotNull(ctx.getBean(InstanceInfo.class));
        }
    }

    @Test
    public void lookupService() {
        try (ConfigurableApplicationContext ctx = createContext(ContextWithApplications.class)) {
            LookupService svc = ctx.getBean(LookupService.class);
            Assert.assertNotNull(svc);

            Assert.assertNotNull(svc.getApplication("app1"));
        }
    }

    private AnnotationConfigApplicationContext createContext(Class<?>... configurations) {
        List<Class<?>> configs = new ArrayList<>(Arrays.asList(BaseApplicationContext.class, PlatformComponents.class));
        configs.addAll(Arrays.asList(configurations));

        return new AnnotationConfigApplicationContext(configs.toArray(new Class<?>[configs.size()]));
    }

    @Configuration
    public static class BaseApplicationContext {
        @Bean
        static PropertySourcesPlaceholderConfigurer ppc() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Configuration
    public static class ContextWithApplications {
        @Bean
        Application app1() {
            Application a1 = new Application("app1");
            a1.addInstance(InstanceInfo.Builder
                    .newBuilder()
                    .setAppName(a1.getName())
                    .setASGName("app1-test")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .setVIPAddress("app1")
                    .setHostName("localhost")
                    .setPort(7001)
                    .build());
            return a1;
        }

        @Bean
        Application app2() {
            Application a2 = new Application("app2");
            a2.addInstance(InstanceInfo.Builder
                    .newBuilder()
                    .setAppName(a2.getName())
                    .setASGName("app2-test")
                    .setStatus(InstanceInfo.InstanceStatus.UP)
                    .setVIPAddress("app2")
                    .setHostName("localhost")
                    .setPort(8001)
                    .build());
            return a2;
        }
    }

}
