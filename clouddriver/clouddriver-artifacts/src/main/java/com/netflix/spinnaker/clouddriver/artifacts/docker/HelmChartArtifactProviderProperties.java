package com.netflix.spinnaker.clouddriver.artifacts.docker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("artifacts.helm-oci")
public class HelmChartArtifactProviderProperties {
  public static final int DEFAULT_CLONE_RETENTION_CHECK_MS = 60000;
  private int cloneRetentionMinutes = 0;
  private int cloneRetentionCheckMs = DEFAULT_CLONE_RETENTION_CHECK_MS;
  private long cloneRetentionMaxBytes = 1024 * 1024 * 100; // 100 MB
  private int cloneWaitLockTimeoutSec = 60;
}
