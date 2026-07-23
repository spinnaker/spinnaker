package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.kork.annotations.Alpha;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires up the stream-based agent scheduler. All the moving parts (scheduler, runner, state
 * machine, admin controller) are components discovered by the scan; this class exists to activate
 * the scan and pull in Spring Boot's redis autoconfiguration, which supplies the
 * RedisConnectionFactory (configured via spring.data.redis.*) and StringRedisTemplate the scheduler
 * and runner inject - clouddriver at large talks to redis through raw jedis (kork-jedis), so those
 * beans exist nowhere else. The runner manages its own consumer threads via
 * StreamMessageListenerContainer - there is no distributed lock: record delivery is handled by the
 * redis stream consumer group, and execution mutual exclusion by the SQL state machine's
 * conditional transitions.
 *
 * <p>This must be a plain {@code @Import}, NOT {@code @ImportAutoConfiguration}: clouddriver's Main
 * excludes RedisAutoConfiguration via {@code @EnableAutoConfiguration(exclude=...)}, and Spring
 * Boot merges exclusions across the whole auto-configuration import group - an
 * {@code @ImportAutoConfiguration} of an excluded class is silently dropped. A direct import
 * bypasses the group (the conditions inside the imported classes still apply).
 */
@Configuration
@ComponentScan(basePackages = "com.netflix.spinnaker.cats.pubsub")
@ConditionalOnProperty("cats.pubsub.enabled")
@Import({RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@Alpha
public class PubSubSchedulerConfig {}
