package com.netflix.spinnaker.front50.config;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storage-service")
public class StorageServiceConfigurationProperties {

  private PerObjectType application = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType applicationPermission = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType serviceAccount = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType project = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType notification = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType pipelineStrategy = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType pipeline = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType pipelineTemplate = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType snapshot = new PerObjectType(2, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType deliveryConfig = new PerObjectType(20, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType pluginInfo = new PerObjectType(2, TimeUnit.MINUTES.toMillis(1));
  private PerObjectType entityTags = new PerObjectType(2, TimeUnit.MINUTES.toMillis(5), false);

  public PerObjectType getApplication() {
    return application;
  }

  public void setApplication(PerObjectType application) {
    this.application = application;
  }

  public PerObjectType getApplicationPermission() {
    return applicationPermission;
  }

  public void setApplicationPermission(PerObjectType applicationPermission) {
    this.applicationPermission = applicationPermission;
  }

  public PerObjectType getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(PerObjectType serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  public PerObjectType getProject() {
    return project;
  }

  public void setProject(PerObjectType project) {
    this.project = project;
  }

  public PerObjectType getNotification() {
    return notification;
  }

  public void setNotification(PerObjectType notification) {
    this.notification = notification;
  }

  public PerObjectType getPipelineStrategy() {
    return pipelineStrategy;
  }

  public void setPipelineStrategy(PerObjectType pipelineStrategy) {
    this.pipelineStrategy = pipelineStrategy;
  }

  public PerObjectType getPipeline() {
    return pipeline;
  }

  public void setPipeline(PerObjectType pipeline) {
    this.pipeline = pipeline;
  }

  public PerObjectType getPipelineTemplate() {
    return pipelineTemplate;
  }

  public void setPipelineTemplate(PerObjectType pipelineTemplate) {
    this.pipelineTemplate = pipelineTemplate;
  }

  public PerObjectType getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(PerObjectType snapshot) {
    this.snapshot = snapshot;
  }

  public PerObjectType getDeliveryConfig() {
    return deliveryConfig;
  }

  public void setDeliveryConfig(PerObjectType deliveryConfig) {
    this.deliveryConfig = deliveryConfig;
  }

  public PerObjectType getPluginInfo() {
    return pluginInfo;
  }

  public void setPluginInfo(PerObjectType pluginInfo) {
    this.pluginInfo = pluginInfo;
  }

  public PerObjectType getEntityTags() {
    return entityTags;
  }

  public void setEntityTags(PerObjectType entityTags) {
    this.entityTags = entityTags;
  }

  public static class PerObjectType {

    private int threadPool;
    private long refreshMs;
    private boolean shouldWarmCache;

    public PerObjectType(int threadPool, long refreshMs) {
      this(threadPool, refreshMs, true);
    }

    public PerObjectType(int threadPool, long refreshMs, boolean shouldWarmCache) {
      setThreadPool(threadPool);
      setRefreshMs(refreshMs);
      setShouldWarmCache(shouldWarmCache);
    }

    public void setThreadPool(int threadPool) {
      if (threadPool <= 1) {
        throw new IllegalArgumentException("threadPool must be >= 1");
      }

      this.threadPool = threadPool;
    }

    public int getThreadPool() {
      return threadPool;
    }

    public long getRefreshMs() {
      return refreshMs;
    }

    public void setRefreshMs(long refreshMs) {
      this.refreshMs = refreshMs;
    }

    public boolean isShouldWarmCache() {
      return shouldWarmCache;
    }

    public boolean getShouldWarmCache() {
      return shouldWarmCache;
    }

    public void setShouldWarmCache(boolean shouldWarmCache) {
      this.shouldWarmCache = shouldWarmCache;
    }
  }
}
