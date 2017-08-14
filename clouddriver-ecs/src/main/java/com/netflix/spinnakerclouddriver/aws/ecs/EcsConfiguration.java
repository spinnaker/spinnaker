package com.netflix.spinnakerclouddriver.aws.ecs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("ecs.enabled")
public class EcsConfiguration {


}
