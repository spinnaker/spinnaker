package com.netflix.kayenta.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Import({
  KayentaConfiguration.class,
  WebConfiguration.class,
})
@ComponentScan({
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.endpoint",
})
@EnableAutoConfiguration
@EnableAsync
@EnableScheduling
public class ApplicationConfiguration {}
