/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.docker;

import com.netflix.spinnaker.clouddriver.artifacts.utilities.AbstractArtifactCachedFileSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class HelmOciFileSystem extends AbstractArtifactCachedFileSystem {

  private static final String CLONES_HOME = "helmcharts";

  private final HelmOciArtifactProviderProperties config;

  public HelmOciFileSystem(HelmOciArtifactProviderProperties properties) {
    super(CLONES_HOME);
    this.config = properties;
  }

  @Override
  public int getCloneWaitLockTimeoutSec() {
    return config.getCloneWaitLockTimeoutSec();
  }

  @Override
  public boolean canRetainClone() {
    return config.getCloneRetentionMinutes() != 0 && hasFreeDisk();
  }

  @Override
  protected long getCloneRetentionMaxBytes() {
    return config.getCloneRetentionMaxBytes();
  }

  @Override
  protected int getCloneRetentionMinutes() {
    return config.getCloneRetentionMinutes();
  }

  @Scheduled(
      fixedDelayString =
          "${artifacts.helm-oci.clone-retention-check-ms:"
              + HelmOciArtifactProviderProperties.DEFAULT_CLONE_RETENTION_CHECK_MS
              + "}")
  private void deleteExpiredHelmCharts() {
    deleteExpiredClones("helm/image chart");
  }
}
