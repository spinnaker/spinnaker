package com.netflix.spinnaker.fiat.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.RedisPermissionRepositoryConfigProps;
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(RedisPermissionRepositoryConfigProps.class)
public class PermissionsRepositoryConfig {

  @ConditionalOnProperty(value = "permissions-repository.redis.enabled", matchIfMissing = true)
  @Bean
  PermissionsRepository redisPermissionsRepository(
      Registry registry,
      @Qualifier("objectMapper") ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      List<Resource> resources,
      RedisPermissionRepositoryConfigProps configProps,
      RetryRegistry retryRegistry) {
    RedisPermissionsRepository redisPermissionsRepository =
        new RedisPermissionsRepository(
            objectMapper, redisClientDelegate, resources, configProps, retryRegistry);
    return InstrumentedProxy.proxy(
        registry,
        redisPermissionsRepository,
        "permissionsRepository",
        Collections.singletonMap("repositoryType", "redis"));
  }
}
