package com.netflix.spinnaker.gate.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import springfox.documentation.swagger.web.InMemorySwaggerResourcesProvider;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;

@Configuration
@ConditionalOnProperty("swagger.enabled")
public class SwaggerEndpointsConfig {

  @Primary
  @Bean
  public SwaggerResourcesProvider swaggerResourcesProvider(
      InMemorySwaggerResourcesProvider defaultResourcesProvider) {
    return () -> {
      SwaggerResource keelApiDocs = new SwaggerResource();
      keelApiDocs.setName("keel");
      keelApiDocs.setSwaggerVersion("3.0");
      keelApiDocs.setLocation("/managed/api-docs");

      List<SwaggerResource> resources = new ArrayList<>(defaultResourcesProvider.get());
      resources.add(keelApiDocs);
      return resources;
    };
  }
}
