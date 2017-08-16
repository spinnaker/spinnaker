package com.netflix.spinnaker.clouddriver.ecs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.netflix.spinnaker.clouddriver.ecs")
@ConditionalOnProperty("ecs.enabled")
public class EcsConfiguration {

}
