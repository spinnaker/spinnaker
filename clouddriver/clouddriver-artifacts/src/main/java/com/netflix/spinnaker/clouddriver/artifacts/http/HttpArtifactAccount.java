/*
 * Copyright 2018 Joel Wilsson
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

package com.netflix.spinnaker.clouddriver.artifacts.http;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.config.BasicAuth;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class HttpArtifactAccount implements ArtifactAccount, BasicAuth {
  private final String name;
  /*
   One of the following are required for auth:
    - username and password
    - usernamePasswordFile : path to file containing "username:password"
  */
  private final Optional<String> username;
  private final Optional<String> password;
  private final Optional<String> usernamePasswordFile;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  public HttpArtifactAccount(
      String name, String username, String password, String usernamePasswordFile) {
    this.name = Strings.nullToEmpty(name);
    this.username = Optional.ofNullable(Strings.emptyToNull(username));
    this.password = Optional.ofNullable(Strings.emptyToNull(password));
    this.usernamePasswordFile = Optional.ofNullable(Strings.emptyToNull(usernamePasswordFile));
  }

  @JsonIgnore
  boolean usesAuth() {
    return username.isPresent() || password.isPresent() || usernamePasswordFile.isPresent();
  }
}
