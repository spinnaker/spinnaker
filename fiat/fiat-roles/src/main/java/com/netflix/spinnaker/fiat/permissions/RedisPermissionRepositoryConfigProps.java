package com.netflix.spinnaker.fiat.permissions;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("fiat.redis")
public class RedisPermissionRepositoryConfigProps {

  private String prefix = "spinnaker:fiat";

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
    private Duration getUserResourceTimeout = Duration.ofSeconds(1);
    private int syncThreads = Runtime.getRuntime().availableProcessors();

    /**
     * Skip rewriting per-user permission keys whose content matches the stored write digest. When
     * disabled every key is rewritten on every put, and digests are still kept accurate so that
     * re-enabling is safe.
     */
    private boolean skipUnchangedWrites = true;

    /**
     * Optional expiry for write digests; unset, zero or negative means they never expire. Fiat
     * versions that predate digests can rewrite a key without updating its digest, so setting this
     * temporarily during a mixed-version rollout or rollback bounds how long such a write could go
     * unnoticed.
     */
    private Duration writeDigestTtl;

    /**
     * Included in every write digest. Changing it invalidates all stored digests, so the next sync
     * rewrites every key; do this when rolling forward after running a Fiat version that predates
     * digests.
     */
    private String writeDigestGeneration = "1";

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

    public Duration getGetUserResourceTimeout() {
      return getUserResourceTimeout;
    }

    public void setGetUserResourceTimeout(Duration getUserResourceTimeout) {
      this.getUserResourceTimeout = getUserResourceTimeout;
    }

    public int getSyncThreads() {
      return syncThreads;
    }

    public void setSyncThreads(int threads) {
      this.syncThreads = threads;
    }

    public boolean isSkipUnchangedWrites() {
      return skipUnchangedWrites;
    }

    public void setSkipUnchangedWrites(boolean skipUnchangedWrites) {
      this.skipUnchangedWrites = skipUnchangedWrites;
    }

    public Duration getWriteDigestTtl() {
      return writeDigestTtl;
    }

    public void setWriteDigestTtl(Duration writeDigestTtl) {
      this.writeDigestTtl = writeDigestTtl;
    }

    public String getWriteDigestGeneration() {
      return writeDigestGeneration;
    }

    public void setWriteDigestGeneration(String writeDigestGeneration) {
      this.writeDigestGeneration = writeDigestGeneration;
    }
  }
}
