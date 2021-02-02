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

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class GitRepoArtifactAccount implements ArtifactAccount {
  private final String name;
  private final String username;
  private final String password;
  private final String token;
  private final String sshPrivateKeyFilePath;
  private final String sshPrivateKeyPassphrase;
  private final String sshPrivateKeyPassphraseCmd;
  private final String sshKnownHostsFilePath;
  private final boolean sshTrustUnknownHosts;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  public GitRepoArtifactAccount(
      String name,
      String username,
      String password,
      String token,
      String sshPrivateKeyFilePath,
      String sshPrivateKeyPassphrase,
      String sshPrivateKeyPassphraseCmd,
      String sshKnownHostsFilePath,
      boolean sshTrustUnknownHosts) {
    this.name = Strings.nullToEmpty(name);
    this.username = Strings.nullToEmpty(username);
    this.password = Strings.nullToEmpty(password);
    this.token = Strings.nullToEmpty(token);
    this.sshPrivateKeyFilePath = Strings.nullToEmpty(sshPrivateKeyFilePath);
    this.sshPrivateKeyPassphrase = Strings.nullToEmpty(sshPrivateKeyPassphrase);
    this.sshPrivateKeyPassphraseCmd = Strings.nullToEmpty(sshPrivateKeyPassphraseCmd);
    this.sshKnownHostsFilePath = Strings.nullToEmpty(sshKnownHostsFilePath);
    this.sshTrustUnknownHosts = sshTrustUnknownHosts;
  }
}
