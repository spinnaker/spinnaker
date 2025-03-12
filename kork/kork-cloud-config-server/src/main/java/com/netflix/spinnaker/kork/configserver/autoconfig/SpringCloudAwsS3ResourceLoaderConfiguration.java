package com.netflix.spinnaker.kork.configserver.autoconfig;

import io.awspring.cloud.context.config.annotation.ContextResourceLoaderConfiguration;
import io.awspring.cloud.context.support.io.SimpleStorageProtocolResolverConfigurer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
@AutoConfigureAfter(ContextResourceLoaderConfiguration.class)
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
