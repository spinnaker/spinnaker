package com.netflix.spinnaker.front50.config;

import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage-service")
@Data
public class StorageServiceConfigurationProperties {

  private PerObjectType application =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType applicationPermission =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType serviceAccount =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType project =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType notification =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType pipelineStrategy =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType pipeline =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType pipelineTemplate =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType snapshot =
      new PerObjectType()
          .setThreadPool(2)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType deliveryConfig =
      new PerObjectType()
          .setThreadPool(20)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType pluginInfo =
      new PerObjectType()
          .setThreadPool(2)
          .setRefreshMs(TimeUnit.MINUTES.toMillis(1))
          .setShouldWarmCache(true);
  private PerObjectType entityTags =
      new PerObjectType().setThreadPool(2).setRefreshMs(TimeUnit.MINUTES.toMillis(5));

  @Data
  @Accessors(chain = true)
  public static class PerObjectType {
    private int threadPool;
    private long refreshMs;
    private boolean shouldWarmCache;
    private long cacheHealthCheckTimeoutSeconds = 90L;

    /**
     * When true, if multiple threads attempt to refresh the cache in StorageServiceSupport
     * simultaneously, only one actually does the refresh. The others wait until it's complete. This
     * reduces load on the data store.
     */
    private boolean synchronizeCacheRefresh;

    /**
     * When true, for objects that support versioning, cache refreshes only query the data store for
     * objects modified (or deleted) since the last refresh.
     */
    private boolean optimizeCacheRefreshes;

    public PerObjectType setThreadPool(int threadPool) {
      if (threadPool <= 1) {
        throw new IllegalArgumentException("threadPool must be >= 1");
      }

      this.threadPool = threadPool;
      return this;
    }
  }
}
