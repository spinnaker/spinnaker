/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.configserver.autoconfig;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.aws.autoconfigure.context.ContextResourceLoaderAutoConfiguration;
import org.springframework.cloud.aws.context.support.io.SimpleStorageProtocolResolverConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The Spring Cloud Config resources API uses a Spring ResourceLoader to provide access to files in
 * AWS S3 using a "s3://" resource scheme. This configuration ensures that this ResourceLoader
 * support is properly initialized and available before Spring starts to load configuration
 * properties.
 *
 * <p>See
 * https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/ResourceLoader.html
 * See
 * https://cloud.spring.io/spring-cloud-static/spring-cloud-aws/2.1.3.RELEASE/single/spring-cloud-aws.html#_resource_handling
 */
@Configuration
@Profile("awss3")
@Conditional(RemoteConfigSourceConfigured.class)
@AutoConfigureAfter(ContextResourceLoaderAutoConfiguration.class)
class SpringCloudAwsS3ResourceLoaderConfiguration {
  @Bean
  public AwsS3ResourceLoaderBeanPostProcessor awsS3ResourceLoaderBeanPostProcessor() {
    return new AwsS3ResourceLoaderBeanPostProcessor();
  }

  private static class AwsS3ResourceLoaderBeanPostProcessor implements BeanFactoryPostProcessor {
    public AwsS3ResourceLoaderBeanPostProcessor() {}

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
        throws BeansException {
      String[] beanNames =
          beanFactory.getBeanNamesForType(SimpleStorageProtocolResolverConfigurer.class);
      for (String beanName : beanNames) {
        beanFactory.getBean(beanName);
      }
    }
  }
}
