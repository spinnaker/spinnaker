package com.netflix.spinnaker.kork.configserver.autoconfig;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("awss3")
@Configuration
class SpringCloudAwsConfiguration {

  @Bean
  public static AmazonS3Client amazonS3(AwsS3EnvironmentProperties s3EnvironmentProperties) {
    return (AmazonS3Client)
        AmazonS3ClientBuilder.standard().withRegion(s3EnvironmentProperties.getRegion()).build();
  }
}
