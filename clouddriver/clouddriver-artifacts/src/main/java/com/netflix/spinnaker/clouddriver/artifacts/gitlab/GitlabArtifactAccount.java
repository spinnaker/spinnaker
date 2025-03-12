/*
 * Copyright 2018 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.artifacts.gitlab;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.config.TokenAuth;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConstructorBinding;

@NonnullByDefault
@Value
public class GitlabArtifactAccount implements ArtifactAccount, TokenAuth {
  private final String name;
  private final Optional<String> token;
  private final Optional<String> tokenFile;

  @Builder
  @ConstructorBinding
  @ParametersAreNullableByDefault
  GitlabArtifactAccount(String name, String token, String tokenFile) {
    this.name = Strings.nullToEmpty(name);
    this.token = Optional.ofNullable(Strings.emptyToNull(token));
    this.tokenFile = Optional.ofNullable(Strings.emptyToNull(tokenFile));
  }
}
