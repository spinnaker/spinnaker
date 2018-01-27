package com.netflix.spinnaker.orca.config;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisPipelineStack;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration public class OrcaPersistenceConfiguration {
  @Bean
  public JedisPipelineStack pipelineStack(
    @Qualifier("redisClientDelegate") RedisClientDelegate redisClientDelegate) {
    return new JedisPipelineStack("PIPELINE_QUEUE", redisClientDelegate);
  }
}
