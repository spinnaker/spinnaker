package com.netflix.spinnaker.igor.config

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class DockerRegistryEnabledCondition implements Condition {
  @Override
  boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    def env = context.getEnvironment()
    def clouddriverUrl = env.getProperty('services.clouddriver.base-url')
    def dockerRegistryEnabled = env.getProperty('docker-registry.enabled', Boolean, false)
    def helmOciRegistryEnabled = env.getProperty('helm-oci-docker-registry.enabled', Boolean, false)
    return clouddriverUrl && (dockerRegistryEnabled || helmOciRegistryEnabled)
  }
}
