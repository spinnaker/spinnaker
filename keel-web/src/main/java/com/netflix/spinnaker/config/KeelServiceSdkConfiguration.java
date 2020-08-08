package com.netflix.spinnaker.config;

import com.netflix.spinnaker.keel.plugins.KeelServiceSdkFactory;
import com.netflix.spinnaker.kork.plugins.sdk.SdkFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KeelServiceSdkConfiguration {
  @Bean
  public static SdkFactory serviceSdkFactory(ApplicationContext applicationContext) {
    return new KeelServiceSdkFactory(applicationContext);
  }
}
