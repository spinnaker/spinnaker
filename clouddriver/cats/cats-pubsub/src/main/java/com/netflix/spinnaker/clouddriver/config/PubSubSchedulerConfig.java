package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.kork.annotations.Alpha;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the stream-based agent scheduler. All the moving parts (scheduler, runner, state
 * machine, admin controller) are components discovered by the scan; this class only exists to
 * activate the scan and make sure a RedisConnectionFactory/RedisTemplate is available. The runner
 * manages its own consumer threads - there is no message listener container and no distributed
 * lock: record delivery is handled by the redis stream consumer group, and execution mutual
 * exclusion by the SQL state machine's conditional transitions.
 */
@Configuration
@ComponentScan(basePackages = "com.netflix.spinnaker.cats.pubsub")
@ConditionalOnProperty("cats.pubsub.enabled")
@ImportAutoConfiguration(
    classes = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@Alpha
public class PubSubSchedulerConfig {}
