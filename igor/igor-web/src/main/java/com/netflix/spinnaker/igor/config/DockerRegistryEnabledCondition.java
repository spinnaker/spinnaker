package com.netflix.spinnaker.igor.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DockerRegistryEnabledCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    String clouddriverUrl = env.getProperty("services.clouddriver.base-url");
    Boolean dockerRegistryEnabled =
        env.getProperty("docker-registry.enabled", Boolean.class, false);
    Boolean helmOciRegistryEnabled =
        env.getProperty("helm-oci-docker-registry.enabled", Boolean.class, false);
    return clouddriverUrl != null && (dockerRegistryEnabled || helmOciRegistryEnabled);
  }
}
