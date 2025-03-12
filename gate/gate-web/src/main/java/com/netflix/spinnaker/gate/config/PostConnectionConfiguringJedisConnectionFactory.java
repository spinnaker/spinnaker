package com.netflix.spinnaker.gate.config;

import com.google.common.base.Splitter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.data.redis.config.ConfigureNotifyKeyspaceEventsAction;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link ConfigureRedisAction} the first time a connection is retrieved.
 *
 * <p>This works around an issue in Spring where it tries to run the {@code ConfigureRedisAction} at
 * startup. If Redis isn't available, the entire server will fail to start. Instead, we'll configure
 * this setting the first time we successfully get a connection.
 *
 * <p>This Redis pool is used for Spring Boot's session management, not for the rate limit storage.
 */
@Component
public class PostConnectionConfiguringJedisConnectionFactory extends JedisConnectionFactory {

  public static final Splitter USER_INFO_SPLITTER = Splitter.on(':');

  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface ConnectionPostProcessor {}

  private final ConfigureRedisAction configureRedisAction;

  private volatile boolean ranConfigureRedisAction;

  @Autowired
  public PostConnectionConfiguringJedisConnectionFactory(
      @Value("${redis.connection:redis://localhost:6379}") String connectionUri,
      @Value("${redis.timeout:2000}") int timeout,
      @ConnectionPostProcessor Optional<ConfigureRedisAction> configureRedisAction) {

    this.configureRedisAction =
        configureRedisAction.orElse(new ConfigureNotifyKeyspaceEventsAction());

    URI redisUri = URI.create(connectionUri);
    setHostName(redisUri.getHost());
    setPort(redisUri.getPort());
    setTimeout(timeout);

    if (redisUri.getUserInfo() != null) {
      List<String> userInfo = USER_INFO_SPLITTER.splitToList(redisUri.getUserInfo());
      if (userInfo.size() >= 2) {
        setPassword(userInfo.get(1));
      }
    }

    if (redisUri.getScheme().equals("rediss")) {
      setUseSsl(true);
    }
  }

  @Override
  protected JedisConnection postProcessConnection(JedisConnection connection) {
    if (!ranConfigureRedisAction) {
      configureRedisAction.configure(connection);
      ranConfigureRedisAction = true;
    }
    return connection;
  }
}
