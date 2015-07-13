package com.netflix.spinnaker.orca.config

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisPipelineStack
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisPipelineStore
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.util.Pool

@Configuration
@CompileStatic
class OrcaPersistenceConfiguration {
  @Bean JedisOrchestrationStore orchestrationStore(JedisCommands jedisCommands,
                                                   Pool<Jedis> jedisPool,
                                                   @Value('${threadPools.orchestrationStore:150}') int threadPoolSize,
                                                   @Value('${threadPools.orchestrationStore:75}') int threadPoolChunkSize,
                                                   ExtendedRegistry
                                                     extendedRegistry) {
    new JedisOrchestrationStore(jedisCommands, jedisPool, new OrcaObjectMapper(), threadPoolSize, threadPoolChunkSize,
                                extendedRegistry)
  }

  @Bean JedisPipelineStore pipelineStore(JedisCommands jedisCommands,
                                         Pool<Jedis> jedisPool,
                                         @Value('${threadPools.pipelineStore:150}') int threadPoolSize,
                                         @Value('${threadPools.pipelineStore:75}') int threadPoolChunkSize,
                                         ExtendedRegistry extendedRegistry) {
    new JedisPipelineStore(jedisCommands, jedisPool, new OrcaObjectMapper(), threadPoolSize, threadPoolChunkSize,
                           extendedRegistry)
  }

  @Bean JedisPipelineStack pipelineStack(Pool<Jedis> jedisPool) {
    new JedisPipelineStack("PIPELINE_QUEUE", jedisPool)
  }
}
