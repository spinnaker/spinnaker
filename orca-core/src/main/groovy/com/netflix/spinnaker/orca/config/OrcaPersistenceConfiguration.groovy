package com.netflix.spinnaker.orca.config

import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisPipelineStack
import groovy.transform.CompileStatic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

@Configuration
@CompileStatic
class OrcaPersistenceConfiguration {
  @Bean JedisPipelineStack pipelineStack(Pool<Jedis> jedisPool) {
    new JedisPipelineStack("PIPELINE_QUEUE", jedisPool)
  }
}
