/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.bitbucket;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.config.BasicAuth;
import com.netflix.spinnaker.clouddriver.artifacts.config.TokenAuth;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class BitbucketArtifactAccount implements ArtifactAccount, BasicAuth, TokenAuth {
  String name;
  Optional<String> username;
  Optional<String> password;
  Optional<String> usernamePasswordFile;
  Optional<String> token;
  Optional<String> tokenFile;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  BitbucketArtifactAccount(
      String name,
      String username,
      String password,
      String usernamePasswordFile,
      String token,
      String tokenFile) {
    this.name = Strings.nullToEmpty(name);
    this.username = Optional.ofNullable(Strings.emptyToNull(username));
    this.password = Optional.ofNullable(Strings.emptyToNull(password));
    this.usernamePasswordFile = Optional.ofNullable(Strings.emptyToNull(usernamePasswordFile));
    this.token = Optional.ofNullable(Strings.emptyToNull(token));
    this.tokenFile = Optional.ofNullable(Strings.emptyToNull(tokenFile));
  }

  @ParametersAreNullableByDefault
  BitbucketArtifactAccount(
      String name, String username, String password, String usernamePasswordFile) {
    this(name, username, password, usernamePasswordFile, null, null);
  }
}
