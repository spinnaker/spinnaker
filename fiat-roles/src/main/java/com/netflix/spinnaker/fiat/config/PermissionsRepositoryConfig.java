package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.RedisPermissionsRepository;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PermissionsRepositoryConfig {
  @Bean
  PermissionsRepository redisPermissionsRepository(
      Registry registry,
      ObjectMapper objectMapper,
      RedisClientDelegate redisClientDelegate,
      List<Resource> resources,
      @Value("${fiat.redis.prefix:spinnaker:fiat}") String prefix) {
    PermissionsRepository repository =
        new RedisPermissionsRepository(objectMapper, redisClientDelegate, resources, prefix);
    return InstrumentedProxy.proxy(
        registry,
        repository,
        "permissionsRepository",
        Collections.singletonMap("repositoryType", "redis"));
  }
}
