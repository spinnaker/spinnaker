/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("artifacts.git-repo")
public class GitRepoArtifactProviderProperties implements ArtifactProvider<GitRepoArtifactAccount> {
  public static final int DEFAULT_CLONE_RETENTION_CHECK_MS = 60000;

  private boolean enabled;
  private int cloneRetentionMinutes = 0;
  private int cloneRetentionCheckMs = DEFAULT_CLONE_RETENTION_CHECK_MS;
  private long cloneRetentionMaxBytes = 1024 * 1024 * 100; // 100 MB
  private int cloneWaitLockTimeoutSec = 60;
  private List<GitRepoArtifactAccount> accounts = new ArrayList<>();
}
