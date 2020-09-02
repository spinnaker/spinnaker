package com.netflix.spinnaker.fiat.permissions;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("fiat.redis")
public class RedisPermissionRepositoryConfigProps {

  private String prefix = "fiat";

  @NestedConfigurationProperty private Repository repository = new Repository();

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public Repository getRepository() {
    return repository;
  }

  public void setRepository(Repository repository) {
    this.repository = repository;
  }

  public static class Repository {
    private Duration getPermissionTimeout = Duration.ofSeconds(1);
    private Duration checkLastModifiedTimeout = Duration.ofMillis(50);

    public Duration getGetPermissionTimeout() {
      return getPermissionTimeout;
    }

    public void setGetPermissionTimeout(Duration getPermissionTimeout) {
      this.getPermissionTimeout = getPermissionTimeout;
    }

    public Duration getCheckLastModifiedTimeout() {
      return checkLastModifiedTimeout;
    }

    public void setCheckLastModifiedTimeout(Duration checkLastModifiedTimeout) {
      this.checkLastModifiedTimeout = checkLastModifiedTimeout;
    }
  }
}
