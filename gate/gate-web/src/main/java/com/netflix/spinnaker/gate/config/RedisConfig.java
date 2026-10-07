package com.netflix.spinnaker.gate.config;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import redis.clients.jedis.JedisPool;

@Configuration
public class RedisConfig extends RedisHttpSessionConfiguration {

  @Value("${server.session.timeout-in-seconds:3600}")
  public void setSessionTimeout(int maxInactiveIntervalInSeconds) {
    super.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
  }

  @Bean
  @ConditionalOnMissingBean(CookieSerializer.class)
  public DefaultCookieSerializer defaultCookieSerializer() {
    return new DefaultCookieSerializer();
  }

  @Autowired
  public RedisConfig(
      @Value("${server.session.timeout-in-seconds:3600}") int maxInactiveIntervalInSeconds) {
    super.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
  }

  /**
   * This pool is used for the rate limit storage, as opposed to the JedisConnectionFactory, which
   * is a separate pool used for Spring Boot's session management.
   */
  @Bean
  public JedisPool jedis(
      @Value("${redis.connection:redis://localhost:6379}") String connection,
      @Value("${redis.timeout:2000}") int timeout)
      throws URISyntaxException {
    return new JedisPool(new URI(connection), timeout);
  }
}
